package pe.saniape.app.data.staff

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor
import kotlin.math.ln

/**
 * Buscar y registrar al paciente por NOMBRE (nueva cita, 2026-10-16) — GEMELO
 * de `lib/paciente-busqueda.ts` de la web (mismos nombres, mismas reglas, los
 * mismos casos en `PacienteBusquedaTest`). El endpoint
 * `GET /api/staff/paciente/buscar` usa esa misma lógica en el servidor: así la
 * web y el celular encuentran y ordenan a los mismos pacientes.
 *
 * Regla de la casa: el DOCUMENTO identifica; el nombre NO, solo sugiere. Por
 * eso aquí nada decide solo: se ordena, se avisa de parecidos y la persona elige.
 *
 * `normalizarBusqueda` (Alimentos.kt) y `distancia` (CalendarioVistaPrevia.kt)
 * ya existían en el paquete con la misma regla que la web: se reusan.
 */

/** Palabras de un nombre ya normalizado. */
private fun palabrasDe(s: String?): List<String> = normalizarBusqueda(s).split(' ').filter { it.isNotEmpty() }

private val RE_DNI = Regex("^\\d{8}$")
private val RE_RUT = Regex("^[\\dkK.\\-\\s]{7,15}$")
private val RE_PASAPORTE = Regex("^[A-Za-z]{1,3}\\d{5,12}$")
private val RE_DIGITOS_K = Regex("^\\d+[kK]?$")

/**
 * ¿Lo escrito es un documento COMPLETO que se puede dar de alta? DNI peruano
 * (8 dígitos), RUT chileno o pasaporte (las reglas de siempre del buscador).
 */
fun esDocumentoCompleto(t: String?): Boolean {
    val s = t.orEmpty().trim()
    return RE_DNI.matches(s) || (s.any { it in '0'..'9' } && RE_RUT.matches(s)) || RE_PASAPORTE.matches(s)
}

/** Qué se escribió en el único campo "DNI o nombre". */
sealed interface EntradaBusqueda {
    data object Vacio : EntradaBusqueda
    /** [doc] tal como se escribió (un RUT lleva puntos y guion). [completo]: se puede registrar. */
    data class Documento(val doc: String, val completo: Boolean) : EntradaBusqueda
    data class Nombre(val palabras: List<String>) : EntradaBusqueda
}

/**
 * Dígitos (con puntos, guiones o la K del RUT) o un pasaporte → documento, el
 * flujo de siempre. Cualquier otra cosa → nombre. Un documento a medio escribir
 * ("4424") sigue siendo documento: filtra, pero todavía no ofrece registrarlo.
 */
fun clasificarEntrada(texto: String?): EntradaBusqueda {
    val t = texto.orEmpty().trim()
    if (t.isEmpty()) return EntradaBusqueda.Vacio
    val compacto = t.filterNot { it == '.' || it == '-' || it.isWhitespace() }
    if (RE_DIGITOS_K.matches(compacto) || RE_PASAPORTE.matches(compacto)) {
        return EntradaBusqueda.Documento(t, esDocumentoCompleto(t))
    }
    val palabras = palabrasDe(t)
    return if (palabras.isNotEmpty()) EntradaBusqueda.Nombre(palabras) else EntradaBusqueda.Vacio
}

/**
 * Qué tan bien coincide un nombre con las palabras buscadas (0 = no coincide).
 * TODAS las palabras deben estar, en cualquier orden. Palabra completa (3) >
 * inicio de palabra (2) > contiene (1); suman el nombre idéntico (+10), no
 * tener palabras de más (+1) y el mismo orden en que se escribió (+1).
 */
fun puntuarNombre(nombre: String, palabras: List<String>): Int {
    if (palabras.isEmpty()) return 0
    val n = normalizarBusqueda(nombre)
    val tokens = n.split(' ').filter { it.isNotEmpty() }
    var total = 0
    for (w in palabras) {
        total += when {
            w in tokens -> 3
            tokens.any { it.startsWith(w) } -> 2
            n.contains(w) -> 1
            else -> return 0
        }
    }
    if (n == palabras.joinToString(" ")) total += 10
    if (tokens.size == palabras.size) total += 1
    var desde = 0
    var enOrden = true
    for (w in palabras) {
        val i = n.indexOf(w, desde)
        if (i < 0) { enOrden = false; break }
        desde = i + w.length
    }
    if (enOrden && palabras.size > 1) total += 1
    return total
}

/** Los que coinciden con el nombre buscado, el mejor primero; en empate, el orden de entrada. */
fun <T> rankearPorNombre(lista: List<T>, palabras: List<String>, max: Int, nombre: (T) -> String): List<T> {
    if (palabras.isEmpty()) return emptyList()
    return lista.mapIndexedNotNull { i, p -> puntuarNombre(nombre(p), palabras).takeIf { it > 0 }?.let { Triple(p, it, i) } }
        .sortedWith(compareByDescending<Triple<T, Int, Int>> { it.second }.thenBy { it.third })
        .take(max).map { it.first }
}

/** Coincidencia por documento: lo escrito (sin espacios) dentro del documento guardado. */
fun coincidePorDocumento(dni: String?, doc: String?): Boolean {
    val d = dni.orEmpty().lowercase()
    val q = doc.orEmpty().filterNot { it.isWhitespace() }.lowercase()
    return d.isNotEmpty() && q.isNotEmpty() && d.contains(q)
}

private val PARTICULAS = setOf("de", "del", "la", "las", "los", "y", "e", "da", "do", "dos", "van", "von")
private const val SEPARADORES_TITULO = "-'’."

/**
 * El nombre con que se registra a alguien SIN documento, o null si no sirve.
 * Al menos 2 palabras de letras (nombre + apellido), sin dígitos, ≤ 120. En
 * formato Título conservando tildes: "ana maría DE LA cruz" → "Ana María de la Cruz".
 */
fun nombreParaAlta(texto: String?): String? {
    val limpio = texto.orEmpty().replace(Regex("\\s+"), " ").trim()
    if (limpio.isEmpty() || limpio.length > 120 || limpio.any { it in '0'..'9' }) return null
    if (!limpio.all { it.isLetter() || it.isWhitespace() || it == '\'' || it == '.' || it == '-' }) return null
    val palabras = limpio.split(' ')
    if (palabras.count { w -> w.count { it.isLetter() } >= 2 } < 2) return null
    return palabras.mapIndexed { i, w ->
        val min = w.lowercase()
        if (i > 0 && min in PARTICULAS) min
        else buildString {
            min.forEachIndexed { j, ch ->
                val inicio = j == 0 || min[j - 1] in SEPARADORES_TITULO
                if (inicio && ch.isLetter()) append(ch.uppercase()) else append(ch)
            }
        }
    }.joinToString(" ")
}

/** 1 iguales, 0.85 a una letra (≥ 4 letras), 0 nada. */
private fun parecidoPalabra(a: String, b: String): Double = when {
    a == b -> 1.0
    a.length >= 4 && b.length >= 4 && distancia(a, b) <= 1 -> 0.85
    else -> 0.0
}

/** Peso de cada palabra según lo RARA que es en la clínica (IDF): "María" pesa poco, "Olivera" mucho. */
fun pesosPorRareza(nombres: List<String>): (String) -> Double {
    val df = HashMap<String, Int>()
    for (n in nombres) for (w in palabrasDe(n).toSet()) df[w] = (df[w] ?: 0) + 1
    val total = nombres.size
    return { w -> ln(1.0 + (total + 1).toDouble() / ((df[w] ?: 0) + 1)) }
}

/** Math.round de JS (mitades hacia arriba), a 2 decimales. */
private fun redondear2(x: Double): Double = floor(x * 100 + 0.5) / 100

/**
 * Similitud de dos nombres (0–1), simétrica, en cualquier orden, medida sobre
 * el MÁS CORTO. Cada palabra pesa según [peso] y tolera una letra.
 */
fun similitudNombres(a: String, b: String, peso: (String) -> Double = { 1.0 }): Double {
    val pa = palabrasDe(a).filter { it.length >= 2 }
    val pb = palabrasDe(b).filter { it.length >= 2 }
    if (pa.isEmpty() || pb.isEmpty()) return 0.0
    val (corto, largo) = if (pa.size <= pb.size) pa to pb else pb to pa
    var logrado = 0.0
    var posible = 0.0
    val usadas = HashSet<Int>()
    for (w in corto) {
        val pw = peso(w)
        posible += pw
        var mejor = 0.0
        var idx = -1
        largo.forEachIndexed { i, x ->
            if (i in usadas) return@forEachIndexed
            val s = parecidoPalabra(w, x)
            if (s > mejor) { mejor = s; idx = i }
        }
        if (idx >= 0) usadas.add(idx)
        logrado += pw * mejor
    }
    return if (posible > 0) redondear2(logrado / posible) else 0.0
}

const val UMBRAL_PARECIDO = 0.6

/** Una ficha parecida y cuánto. */
data class Parecido<T>(val paciente: T, val similitud: Double)

/**
 * Antes de registrar SIN documento: fichas con un nombre muy parecido ("¿Es
 * alguno de estos?"). Avisa, no bloquea. El más parecido primero, como mucho [max].
 */
fun <T> parecidosA(
    lista: List<T>, nombre: String, umbral: Double = UMBRAL_PARECIDO, max: Int = 5, nombreDe: (T) -> String,
): List<Parecido<T>> {
    if (palabrasDe(nombre).isEmpty()) return emptyList()
    val peso = pesosPorRareza(lista.map(nombreDe))
    return lista.mapIndexed { i, p -> Triple(p, similitudNombres(nombre, nombreDe(p), peso), i) }
        .filter { it.second >= umbral }
        .sortedWith(compareByDescending<Triple<T, Double, Int>> { it.second }.thenBy { it.third })
        .take(max)
        .map { Parecido(it.first, it.second) }
}

/** "45781234" → "••••1234". */
fun enmascararDni(dni: String?): String? {
    val s = dni.orEmpty().trim()
    if (s.isEmpty()) return null
    return if (s.length <= 4) "••${s.takeLast(2)}" else "••••${s.takeLast(4)}"
}

/** "+51 940 202 780" → "••• ••• 780". Null si no parece un teléfono. */
fun enmascararTelefono(tel: String?): String? {
    val d = tel.orEmpty().filter { it in '0'..'9' }
    if (d.length < 6) return null
    return "••• ••• ${d.takeLast(3)}"
}

private val RE_FECHA = Regex("^\\d{4}-\\d{2}-\\d{2}$")

/** Edad cumplida en [hoy] (ambas YYYY-MM-DD) — `edadEn` de lib/cumpleanios. */
fun edadEn(fecha: String, hoy: String): Int {
    val (a, m, d) = fecha.split('-').map { it.toInt() }
    val (ha, hm, hd) = hoy.split('-').map { it.toInt() }
    return ha - a - (if (hm < m || (hm == m && hd < d)) 1 else 0)
}

/** Fecha real, no futura y de alguien con menos de 120 años — `fechaNacimientoValida`. */
fun fechaNacimientoValida(fecha: String?, hoy: String): Boolean {
    if (fecha == null || !RE_FECHA.matches(fecha)) return false
    if (runCatching { LocalDate.parse(fecha) }.getOrNull()?.toString() != fecha) return false
    if (fecha > hoy) return false
    return edadEn(fecha, hoy) < 120
}

/** Edad: de la fecha de nacimiento si es válida; si no, la edad guardada. */
fun edadPaciente(fechaNacimiento: String?, edad: Int?, hoy: String): Int? {
    if (fechaNacimiento != null && fechaNacimientoValida(fechaNacimiento, hoy)) return edadEn(fechaNacimiento, hoy)
    return edad?.takeIf { it in 0..129 }
}

/** Patrón ILIKE sin `unaccent`: vocales y ñ como comodín de UNA letra ("maria" → "%m_r__%"). */
fun patronIlikeSinTildes(palabra: String): String {
    val w = normalizarBusqueda(palabra).filterNot { it.isWhitespace() }
    return "%" + w.map { if (it in "aeioun") '_' else it }.joinToString("") + "%"
}

/** Un alta por nombre en curso: su clave se conserva SOLO para reintentar ESA alta. */
data class IntentoAlta(val nombreNorm: String, val clave: String)

/**
 * Clave de idempotencia del alta por nombre: se genera al tocar "registrar" y
 * se REUSA solo para reintentar esa misma alta no confirmada (corte, 5xx).
 * Tras un alta exitosa quien llama descarta el intento (null): un homónimo
 * registrado a propósito lleva OTRA clave y crea OTRA ficha.
 */
fun claveDeIntento(pendiente: IntentoAlta?, nombre: String, generar: () -> String): IntentoAlta {
    val nombreNorm = normalizarBusqueda(nombre)
    if (pendiente != null && pendiente.nombreNorm == nombreNorm) return pendiente
    return IntentoAlta(nombreNorm, "alta-nombre:${generar()}:${nombreNorm.replace(Regex("\\s+"), "-")}".take(200))
}

/**
 * ¿Con un intento fallido se conserva la clave? Un "no" del servidor (4xx de
 * validación o permiso) no se reintenta: se descarta. Un 5xx, un 409 (parcial)
 * o sin respuesta (status 0) sí la conservan. Como `crearYElegir` de la web.
 */
fun conservarIntento(status: Int): Boolean = !(status in 400..499 && status != 409)

// ─── GET /api/staff/paciente/buscar ─────────────────────────────────────────

/** Una coincidencia del servidor (contrato arriba de la ruta en la web). */
data class CoincidenciaPaciente(
    val id: String,
    val nombre: String,
    val estado: String?,
    val sedeId: String?,
    val tieneDni: Boolean,
    val dniMascara: String?,
    val telefonoMascara: String?,
    val edad: Int?,
    /** YYYY-MM-DD */
    val ultimaCita: String?,
    val similitud: Double? = null,
) {
    val deBaja: Boolean get() = estado == "Inactivo"
}

/** Respuesta del buscador: lista, endpoint que aún no existe (servidor viejo) o error. */
sealed interface RespuestaBusqueda {
    data class Ok(val resultados: List<CoincidenciaPaciente>, val truncado: Boolean) : RespuestaBusqueda
    /** 404 sin `codigo`: el servidor todavía no tiene el endpoint. */
    data object NoDisponible : RespuestaBusqueda
    data class Error(val mensaje: String, val codigo: String?) : RespuestaBusqueda
}

private fun JsonObject.textoB(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

/** Respuesta HTTP de GET /api/staff/paciente/buscar → [RespuestaBusqueda]. Puro. */
fun interpretarBusqueda(status: Int, cuerpo: String?): RespuestaBusqueda {
    val o = cuerpo?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    if (status == 404 && o?.textoB("codigo") == null) return RespuestaBusqueda.NoDisponible
    if (status !in 200..299 || o == null) {
        return RespuestaBusqueda.Error(o?.textoB("error") ?: "No se pudo buscar", o?.textoB("codigo"))
    }
    val lista = (o["resultados"] as? JsonArray).orEmpty().mapNotNull { e ->
        val x = e as? JsonObject ?: return@mapNotNull null
        val id = x.textoB("id") ?: return@mapNotNull null
        CoincidenciaPaciente(
            id = id,
            nombre = x.textoB("nombre") ?: return@mapNotNull null,
            estado = x.textoB("estado"),
            sedeId = x.textoB("sede_id"),
            tieneDni = x.textoB("tiene_dni") == "true",
            dniMascara = x.textoB("dni_mascara"),
            telefonoMascara = x.textoB("telefono_mascara"),
            edad = x.textoB("edad")?.toDoubleOrNull()?.toInt(),
            ultimaCita = x.textoB("ultima_cita"),
            similitud = x.textoB("similitud")?.toDoubleOrNull(),
        )
    }
    return RespuestaBusqueda.Ok(lista, o.textoB("truncado") == "true")
}

/** "2026-10-12" → "12/10/2026". */
fun fechaDmyBusqueda(iso: String?): String? {
    val s = iso?.take(10) ?: return null
    if (!RE_FECHA.matches(s)) return null
    return "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}"
}

/**
 * Lo que distingue a un homónimo en la lista: "DNI ••••5678 · 34 años · Tel.
 * ••• ••• 780 · última cita 12/10/2026". DNI y teléfono solo con
 * [verDatos] (`datos_personales`, lo mismo que decide el servidor). [dniLocal]
 * es el de la lista precargada; si se buscó por documento se muestra entero.
 */
fun datosHomonimo(
    verDatos: Boolean, porDocumento: Boolean, dniLocal: String?, info: CoincidenciaPaciente?,
): String {
    val partes = mutableListOf<String>()
    if (verDatos) {
        val dni = dniLocal?.trim()?.takeIf { it.isNotEmpty() }
        partes += when {
            dni != null -> "DNI " + (if (porDocumento) dni else enmascararDni(dni))
            info != null && info.tieneDni -> "DNI " + (info.dniMascara ?: "••••")
            else -> "sin DNI"
        }
    }
    info?.edad?.let { partes += if (it == 1) "1 año" else "$it años" }
    if (verDatos) info?.telefonoMascara?.let { partes += "Tel. $it" }
    fechaDmyBusqueda(info?.ultimaCita)?.let { partes += "última cita $it" }
    return partes.joinToString(" · ")
}
