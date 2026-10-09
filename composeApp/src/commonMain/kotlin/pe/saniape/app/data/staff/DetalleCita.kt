package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.ui.urlEncode

/**
 * Detalle de una cita al tocarla en la agenda (gemelo de `PopupCita` de /citas web).
 *
 * La lista de la agenda NO trae notas ni diagnóstico (no se engorda la consulta del
 * día): esto se carga al abrir el detalle, por id, con [AgendaRepo.detalleCita].
 */
data class DatosDetalleCita(
    /** `citas.notas`: lo que la web y la app llaman "Observaciones" de la cita. */
    val notas: String? = null,
    /** `citas.diagnostico` (lo clínico: solo llega si quien mira puede verlo). */
    val diagnosticoCita: String? = null,
    /** El del tratamiento de la cita: respaldo para la cita que evalúa. */
    val diagnosticoTratamiento: String? = null,
    val sesionNumero: Int? = null,
    /** `sesiones.notas`: técnicas/procedimientos aplicados. */
    val sesionNotas: String? = null,
    /** `sesiones.mejorias`: cómo evolucionó. */
    val sesionMejorias: String? = null,
)

/** Lo que el detalle pinta debajo de los datos de la cita, en orden. */
sealed class BloqueDetalleCita {
    /**
     * Observaciones de la cita. [direccion] = el tramo que parece una dirección
     * (atención a domicilio): se destaca y ofrece abrirlo en Maps con [urlMaps].
     * [largo] = se muestra recortado con "Ver más".
     */
    data class Observaciones(
        val texto: String,
        val direccion: String?,
        val urlMaps: String?,
        val largo: Boolean,
    ) : BloqueDetalleCita()

    data class Diagnostico(val texto: String) : BloqueDetalleCita()

    /** Notas de la sesión, ya resumidas (breves). */
    data class NotasSesion(val numero: Int?, val tecnicas: String?, val evolucion: String?) : BloqueDetalleCita()
}

/**
 * ¿Quien mira ve lo clínico (diagnóstico, notas de la sesión) en el detalle?
 * Misma regla que el popup de la web (`PopupCita`): el permiso `sesiones`. Quien no
 * lo tiene ve solo las observaciones de la cita, que es lo que necesita para coordinar.
 */
fun veClinicoEnDetalleCita(puedeSesiones: Boolean): Boolean = puedeSesiones

/**
 * Arma los bloques del detalle. Sin datos → lista vacía (no se pinta nada).
 * [citaEvalua] = Evaluación (o la cita que evalúa según el flujo de la especialidad).
 */
fun bloquesDetalleCita(
    tipo: String?,
    citaEvalua: Boolean,
    datos: DatosDetalleCita?,
    verClinico: Boolean,
): List<BloqueDetalleCita> {
    if (datos == null) return emptyList()
    return buildList {
        datos.notas?.trim()?.takeIf { it.isNotEmpty() }?.let { t ->
            val dir = direccionEnTexto(t)
            add(BloqueDetalleCita.Observaciones(
                texto = t,
                direccion = dir,
                urlMaps = dir?.let { urlMapsDe(t, it) },
                largo = necesitaVerMas(t),
            ))
        }
        if (!verClinico) return@buildList
        val diag = datos.diagnosticoCita?.trim()?.takeIf { it.isNotEmpty() }
            ?: datos.diagnosticoTratamiento?.trim()?.takeIf { citaEvalua && it.isNotEmpty() }
        diag?.let { add(BloqueDetalleCita.Diagnostico(it)) }
        if (tipo == "Sesión") {
            val tec = datos.sesionNotas?.let { breve(it) }
            val evo = datos.sesionMejorias?.let { breve(it) }
            if (tec != null || evo != null) add(BloqueDetalleCita.NotasSesion(datos.sesionNumero, tec, evo))
        }
    }
}

// ── Dirección en las observaciones ──

private val ETIQUETA_DIRECCION = Regex(
    """(?:direcci[oó]n|domicilio|direc\.?)\s*[:\-]\s*""", RegexOption.IGNORE_CASE,
)

/** Vías y referencias de dirección (Perú y alrededores). */
private val VIA = Regex(
    """(?:^|[^\p{L}])(av\.?|avda\.?|avenida|jr\.?|jir[oó]n|calle|psje\.?|pje\.?|pasaje|prol\.?|prolongaci[oó]n|""" +
        """urb\.?|urbanizaci[oó]n|aa\.?\s?hh\.?|asoc\.?|asociaci[oó]n|coop\.?|cooperativa|mz\.?|manzana|""" +
        """lt\.?|lote|carretera|km\.?|condominio|edificio|edif\.?|residencial)(?=[^\p{L}]|$)""",
    RegexOption.IGNORE_CASE,
)

/** Un número que NO es una dirección: "hace 3 días", "2 sesiones", "5 kg"… */
private val NUMERO_NO_DIRECCION = Regex(
    """^\s*(d[ií]as?|semanas?|mes(es)?|a[ñn]os?|horas?|hrs?|min|minutos|veces|sesi[oó]n(es)?|kg|cm|mm|mg|ml|%)(?![\p{L}])""",
    RegexOption.IGNORE_CASE,
)

private val NUMERO = Regex("""\d+""")

private val ENLACE_MAPS = Regex(
    """https?://(?:www\.)?(?:google\.[a-z.]+/maps|maps\.google\.[a-z.]+|maps\.app\.goo\.gl|goo\.gl/maps)\S*""",
    RegexOption.IGNORE_CASE,
)

/** Los tramos del texto: líneas y los " · " con que la web une motivo y notas. */
private fun tramos(texto: String): List<String> =
    texto.split('\n').flatMap { it.split(" · ") }.map { it.trim() }.filter { it.isNotEmpty() }

/** ¿Este tramo parece una dirección? */
fun pareceDireccion(tramo: String): Boolean {
    val t = tramo.trim()
    if (t.isEmpty()) return false
    if (ENLACE_MAPS.containsMatchIn(t)) return true
    ETIQUETA_DIRECCION.find(t)?.let { m ->
        if (t.substring(m.range.last + 1).trim().length >= 3) return true
    }
    if (t.length > 160) return false
    // Una vía seguida (cerca) de un número que no sea de tiempo/medida.
    for (m in VIA.findAll(t)) {
        val resto = t.substring(m.range.last + 1).take(40)
        for (n in NUMERO.findAll(resto)) {
            val despues = resto.substring(n.range.last + 1)
            if (!NUMERO_NO_DIRECCION.containsMatchIn(despues)) return true
        }
    }
    return false
}

/**
 * La dirección dentro de las observaciones (el primer tramo que lo parezca), sin
 * la etiqueta "Dirección:". null si no hay.
 */
fun direccionEnTexto(texto: String): String? {
    val tramo = tramos(texto).firstOrNull { pareceDireccion(it) } ?: return null
    val sinEtiqueta = ETIQUETA_DIRECCION.find(tramo)?.let { tramo.substring(it.range.last + 1) } ?: tramo
    return sinEtiqueta.trim().trimEnd('.', ',', ';').trim().ifEmpty { null }
}

/**
 * Lo que se busca en Maps: la dirección sin la referencia ("ref. frente al
 * parque", lo que va entre paréntesis), que confunde al buscador.
 */
fun consultaMaps(direccion: String): String =
    direccion
        .split(Regex("""\(|(?:^|[\s,;])(?:ref\.?|referencia)(?=[\s:.,]|$)""", RegexOption.IGNORE_CASE))
        .first()
        .trim().trimEnd('.', ',', ';', '-').trim()
        .ifEmpty { direccion.trim() }

/** URL para abrir la dirección en Maps (o el enlace de Maps que ya vino en el texto). */
fun urlMapsDe(texto: String, direccion: String): String =
    ENLACE_MAPS.find(texto)?.value?.trimEnd('.', ',', ')')
        ?: "https://www.google.com/maps/search/?api=1&query=${urlEncode(consultaMaps(direccion))}"

// ── Texto largo / breve ──

/** Observaciones largas: se muestran recortadas con "Ver más". */
fun necesitaVerMas(texto: String, maxLineas: Int = 4, maxCaracteres: Int = 220): Boolean =
    texto.trim().lines().size > maxLineas || texto.trim().length > maxCaracteres

/** Resume un texto a [max] caracteres (corta en un espacio y añade "…"). null si está vacío. */
fun breve(texto: String, max: Int = 160): String? {
    val t = texto.trim().replace(Regex("""\s*\n\s*"""), " · ").replace(Regex("""\s{2,}"""), " ")
    if (t.isEmpty()) return null
    if (t.length <= max) return t
    val corte = t.lastIndexOf(' ', max).takeIf { it > max / 2 } ?: max
    return t.take(corte).trimEnd(' ', '·', ',', ';') + "…"
}

// ── Lectura ──

/** Columnas del detalle: [clinico] = false no pide nada clínico a la base. */
fun columnasDetalleCita(clinico: Boolean): String =
    if (clinico) "notas, diagnostico, " +
        "tratamiento:tratamientos!citas_tratamiento_id_fkey(diagnostico), " +
        "sesion:sesiones!citas_sesion_id_fkey(numero, notas, mejorias)"
    else "notas"

fun parsearDetalleCita(o: JsonObject): DatosDetalleCita {
    fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }
    val trat = o["tratamiento"] as? JsonObject
    val ses = o["sesion"] as? JsonObject
    return DatosDetalleCita(
        notas = o.s("notas"),
        diagnosticoCita = o.s("diagnostico"),
        diagnosticoTratamiento = trat?.s("diagnostico"),
        sesionNumero = ses?.s("numero")?.toIntOrNull(),
        sesionNotas = ses?.s("notas"),
        sesionMejorias = ses?.s("mejorias"),
    )
}
