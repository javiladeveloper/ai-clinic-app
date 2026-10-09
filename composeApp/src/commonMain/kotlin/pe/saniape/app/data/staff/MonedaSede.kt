package pe.saniape.app.data.staff

import kotlin.math.roundToLong

/**
 * MULTIPAÍS EN LA APP (Etapa 2): de dónde sale la moneda/país de lo que se
 * muestra, y las piezas por país (prefijo de WhatsApp, documento, catálogo de
 * países y monedas para Ajustes → Sedes).
 *
 * Orden para la moneda de una fila (gemelo de la web):
 *     sede_id de la fila → sede activa → sede principal → clínica → PEN
 * La resolución sede → principal → clínica la hace [monedaDeSede] (base,
 * ZonaClinica.kt). Una clínica de un solo local (DALU) no tiene sedes en el
 * contexto: todo sale PEN / Perú / Lima, exactamente como antes.
 *
 * Los formateadores de dinero viven en Dinero.kt (base). Acá solo los "con
 * moneda" que necesitaban las pantallas sin cambiar el número que mostraban.
 */

// ── Moneda y país vigentes ──────────────────────────────────────────────────

/** Sede activa elegida ("" = consolidado o sin multisede → null). */
private fun sedeActivaDe(ctx: ContextoStaff): String? =
    if (ctx.multiSede) SedeActiva.estado.value.sedeId.takeIf { it.isNotBlank() } else null

/**
 * Moneda de una fila, puro (para los tests): su sede → la activa → principal →
 * clínica → PEN. Sin contexto → PEN.
 */
fun monedaDeFila(ctx: ContextoStaff?, sedeActiva: String?, sedeFila: String?): String {
    if (ctx == null) return MONEDA_POR_DEFECTO
    val id = sedeFila?.takeIf { it.isNotBlank() } ?: sedeActiva?.takeIf { it.isNotBlank() }
    return ctx.monedaDeSede(id)
}

/** País de una fila, puro: su sede → la activa → principal → clínica → PE. */
fun paisDeFila(ctx: ContextoStaff?, sedeActiva: String?, sedeFila: String?): String {
    if (ctx == null) return PAIS_POR_DEFECTO
    val id = sedeFila?.takeIf { it.isNotBlank() } ?: sedeActiva?.takeIf { it.isNotBlank() }
    return ctx.paisDeSede(id)
}

/** Moneda de la sede activa (consolidado / un solo local → principal → clínica → PEN). */
fun monedaActiva(): String {
    val ctx = StaffContextoRepo.actual ?: return MONEDA_POR_DEFECTO
    return monedaDeFila(ctx, sedeActivaDe(ctx), null)
}

/** Moneda de una fila con `sede_id` (cita, movimiento, tratamiento, paciente). */
fun monedaDeFila(sedeId: String?): String {
    val ctx = StaffContextoRepo.actual ?: return MONEDA_POR_DEFECTO
    return monedaDeFila(ctx, sedeActivaDe(ctx), sedeId)
}

/** País (ISO-2) de la sede activa. */
fun paisActivo(): String {
    val ctx = StaffContextoRepo.actual ?: return PAIS_POR_DEFECTO
    return paisDeFila(ctx, sedeActivaDe(ctx), null)
}

/** País de una fila con `sede_id` (p. ej. el paciente). */
fun paisDeFila(sedeId: String?): String {
    val ctx = StaffContextoRepo.actual ?: return PAIS_POR_DEFECTO
    return paisDeFila(ctx, sedeActivaDe(ctx), sedeId)
}

/** ¿Las sedes de la clínica cobran en más de una moneda? (consolidado: un total por moneda). */
fun hayVariasMonedas(): Boolean = (StaffContextoRepo.actual?.monedasEnUso?.size ?: 1) > 1

/** ¿Estamos viendo "todas las sedes" con sedes que cobran en monedas distintas? */
fun consolidadoMultimoneda(): Boolean {
    val ctx = StaffContextoRepo.actual ?: return false
    return ctx.multiSede && sedeActivaDe(ctx) == null && hayVariasMonedas()
}

/** Símbolo de la moneda activa ("S/" en una clínica peruana). */
fun simboloActivo(): String = simboloMoneda(monedaActiva())

/** Monto con la moneda de la sede activa (PEN: idéntico a [soles]). */
fun dineroActivo(monto: Double?): String = formatearDinero(monto, monedaActiva())

/** Monto con la moneda de la sede de la fila (sin sede → la activa). */
fun dineroDeFila(monto: Double?, sedeId: String?): String = formatearDinero(monto, monedaDeFila(sedeId))

// ── Números de dinero "cortos" (los que se escribían a mano junto a "S/") ──

/**
 * 500.0 → "500"; 49.9 → "49.90". El número de las etiquetas de promociones y
 * catálogos ("10 sesiones a S/500"). Unifica los `fmt`/`fmtNum` que había
 * repetidos (misma salida).
 */
fun montoCorto(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else {
    val c = kotlin.math.round(n * 100).toLong()
    "${c / 100}.${(c % 100).toString().padStart(2, '0')}"
}

/** "S/500", "Bs50": símbolo pegado al número corto (como lo escribía la web). */
fun dineroCorto(n: Double, moneda: String? = MONEDA_POR_DEFECTO): String = "${simboloMoneda(moneda)}${montoCorto(n)}"

/**
 * Dinero para gráficos y titulares: "S/ 28,349" en detalle, "S/ 28.3k" en el eje
 * (compacto, desde mil). Para PEN es exactamente lo que daba `solesGrafico`.
 */
fun dineroGrafico(n: Double, moneda: String? = MONEDA_POR_DEFECTO, compacto: Boolean = false): String {
    val s = simboloMoneda(moneda)
    if (compacto && kotlin.math.abs(n) >= 1000) {
        val miles = n / 1000
        val txt = if (n % 1000 == 0.0) miles.roundToLong().toString()
        else {
            val decimas = (miles * 10).roundToLong()
            val ent = decimas / 10
            val dec = kotlin.math.abs(decimas % 10)
            if (dec == 0L) ent.toString() else "${if (decimas < 0 && ent == 0L) "-" else ""}$ent.$dec"
        }
        return "$s ${txt}k"
    }
    return "$s ${entero(n)}"
}

/**
 * Céntimos → texto con su moneda, signo ignorado. PEN: "S/ 20.00" tal cual lo
 * mostraba `solesDeCentimos` (sin separador de miles: los textos del cobro
 * dividido calcan los del servidor); otra moneda: [formatearDinero].
 */
fun dineroDeCentimos(centimos: Long, moneda: String? = MONEDA_POR_DEFECTO): String {
    val c = kotlin.math.abs(centimos)
    return if (normalizarMoneda(moneda) == MONEDA_POR_DEFECTO) "S/ ${c / 100}.${(c % 100).toString().padStart(2, '0')}"
    else formatearDinero(c / 100.0, moneda)
}

// ── Teléfono por país ───────────────────────────────────────────────────────

/** Código telefónico internacional del país (sin "+"). Desconocido → 51 (Perú). */
fun prefijoTelefonico(pais: String?): String = when (pais?.trim()?.uppercase()) {
    "BO" -> "591"
    "CL" -> "56"
    "CO" -> "57"
    "EC" -> "593"
    "MX" -> "52"
    "AR" -> "54"
    "ES" -> "34"
    "US" -> "1"
    else -> "51"
}

/** Dígitos de un número nacional (sin prefijo de país). */
private fun largoNacional(pais: String?): Int = when (pais?.trim()?.uppercase()) {
    "BO" -> 8
    "CO", "MX", "AR", "US" -> 10
    else -> 9          // PE, CL, EC, ES
}

/**
 * Número para wa.me: un número nacional lleva delante el código del país de la
 * sede. Perú: la regla de siempre (9 dígitos o menos → "51…"). Bolivia: un
 * celular de 8 dígitos → "591…". Lo que ya trae el código queda igual.
 */
fun numeroWhatsApp(telefono: String, pais: String? = PAIS_POR_DEFECTO): String {
    val d = telefono.filter { it.isDigit() }
    val p = pais?.trim()?.uppercase().orEmpty().ifEmpty { PAIS_POR_DEFECTO }
    if (p == PAIS_POR_DEFECTO) return if (d.length <= 9) "51$d" else d
    val nacional = d.trimStart('0')
    return if (nacional.length <= largoNacional(p)) prefijoTelefonico(p) + nacional else d
}

/**
 * Enlace de WhatsApp al paciente según el país. Perú: exactamente
 * [enlaceWhatsApp] (menos de 9 dígitos = null). Otro país: menos de 7 dígitos =
 * null; el número nacional lleva su código de país.
 */
fun enlaceWhatsAppPais(contacto: String?, pais: String?, texto: String? = null): String? {
    val p = pais?.trim()?.uppercase().orEmpty().ifEmpty { PAIS_POR_DEFECTO }
    if (p == PAIS_POR_DEFECTO) return enlaceWhatsApp(contacto, texto)
    val d = contacto.orEmpty().filter { it.isDigit() }
    if (d.length < 7) return null
    val base = "https://wa.me/${numeroWhatsApp(d, p)}"
    val t = texto?.trim().orEmpty()
    return if (t.isNotEmpty()) "$base?text=${codificarUrl(t)}" else base
}

// ── Documento de identidad por país ────────────────────────────────────────

/** ¿Se busca el documento en RENIEC (8 dígitos)? Solo en sedes de Perú. */
fun usaReniec(pais: String?): Boolean = (pais?.trim()?.uppercase() ?: PAIS_POR_DEFECTO) == PAIS_POR_DEFECTO

/** Nombre del documento nacional: "DNI" (Perú), "CI" (Bolivia), "RUT" (Chile)… */
fun nombreDocumentoNacional(pais: String?): String = when (pais?.trim()?.uppercase()) {
    "BO", "EC" -> "CI"
    "CL" -> "RUT"
    "CO" -> "CC"
    "MX" -> "CURP"
    "AR" -> "DNI"
    "ES" -> "DNI"
    "US" -> "ID"
    else -> "DNI"
}

// ── Catálogo para Ajustes → Sedes (gemelo de lib/multipais.ts y MONEDAS) ──

data class PaisSoportado(val codigo: String, val nombre: String, val moneda: String, val zonas: List<String>)

val PAISES_SOPORTADOS: List<PaisSoportado> = listOf(
    PaisSoportado("PE", "Perú", "PEN", listOf("America/Lima")),
    PaisSoportado("BO", "Bolivia", "BOB", listOf("America/La_Paz")),
    PaisSoportado("CL", "Chile", "CLP", listOf("America/Santiago", "America/Punta_Arenas")),
    PaisSoportado("CO", "Colombia", "COP", listOf("America/Bogota")),
    PaisSoportado("EC", "Ecuador", "USD", listOf("America/Guayaquil")),
    PaisSoportado("MX", "México", "MXN", listOf("America/Mexico_City", "America/Cancun", "America/Monterrey", "America/Tijuana")),
    PaisSoportado("AR", "Argentina", "ARS", listOf("America/Argentina/Buenos_Aires")),
    PaisSoportado("ES", "España", "EUR", listOf("Europe/Madrid")),
    PaisSoportado("US", "Estados Unidos", "USD", listOf("America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles")),
)

/** Monedas elegibles (lib/configuracion-ajustes.ts → MONEDAS). */
val MONEDAS_ELEGIBLES: List<Pair<String, String>> = listOf(
    "PEN" to "Soles (PEN)",
    "BOB" to "Bolivianos (BOB)",
    "USD" to "Dólares (USD)",
    "MXN" to "Pesos Mexicanos (MXN)",
    "COP" to "Pesos Colombianos (COP)",
    "CLP" to "Pesos Chilenos (CLP)",
    "ARS" to "Pesos Argentinos (ARS)",
    "EUR" to "Euros (EUR)",
)

/** Todas las zonas del catálogo, sin repetir. */
val ZONAS_SOPORTADAS: List<String> = PAISES_SOPORTADOS.flatMap { it.zonas }.distinct()

fun paisPorCodigo(codigo: String?): PaisSoportado? {
    val c = codigo?.trim()?.uppercase() ?: return null
    return PAISES_SOPORTADOS.firstOrNull { it.codigo == c }
}

/** Lo que se edita de una sede: "" = el/la de la clínica (NULL en la base). */
data class RegionalSedeForm(val pais: String = "", val moneda: String = "", val zona: String = "")

/** Al elegir un país se proponen su moneda y su primera zona (se pueden cambiar). */
fun elegirPaisSede(codigo: String): RegionalSedeForm {
    val p = paisPorCodigo(codigo) ?: return RegionalSedeForm()
    return RegionalSedeForm(p.codigo, p.moneda, p.zonas.first())
}

/** Valida como `payloadRegionalSede` de la web. null = válido; si no, el mensaje. */
fun errorRegionalSede(v: RegionalSedeForm): String? {
    val pais = v.pais.trim().uppercase()
    val moneda = v.moneda.trim().uppercase()
    val zona = v.zona.trim()
    if (pais.isNotEmpty() && !(pais.length == 2 && pais.all { it in 'A'..'Z' })) return "País no válido"
    if (moneda.isNotEmpty() && !(moneda.length == 3 && moneda.all { it in 'A'..'Z' })) return "Moneda no válida"
    if (zona.isNotEmpty() && !esZonaValida(zona)) return "Zona horaria no válida"
    return null
}

/** "soles", "bolivianos", "dólares"… para textos como "Descuento en soles". */
fun nombreMonedaPlural(moneda: String? = MONEDA_POR_DEFECTO): String = when (normalizarMoneda(moneda)) {
    "PEN" -> "soles"
    "BOB" -> "bolivianos"
    "USD" -> "dólares"
    "EUR" -> "euros"
    "CLP", "COP", "MXN", "ARS" -> "pesos"
    else -> normalizarMoneda(moneda)
}

/** Bandera (emoji) de un país ISO-2: "PE" → 🇵🇪. */
fun banderaPais(pais: String?): String {
    val p = pais?.trim()?.uppercase().orEmpty()
    if (p.length != 2 || !p.all { it in 'A'..'Z' }) return "🌎"
    return p.map { ch -> "\uD83C" + (0xDDE6 + (ch - 'A')).toChar() }.joinToString("")
}

/**
 * Documentos que se ofrecen al registrar un paciente, el primero por defecto.
 * Perú: DNI (busca en RENIEC) / RUT / pasaporte, como siempre. Otro país: su
 * documento nacional (Bolivia: CI) y pasaporte, sin búsqueda en RENIEC.
 */
fun opcionesDocumento(pais: String?): List<Pair<String, String>> {
    val p = pais?.trim()?.uppercase().orEmpty().ifEmpty { PAIS_POR_DEFECTO }
    return when (p) {
        PAIS_POR_DEFECTO -> listOf("PE" to "🇵🇪 DNI", "CL" to "🇨🇱 RUT", "OTRO" to "🌎 Pasaporte")
        else -> listOf(p to "${banderaPais(p)} ${nombreDocumentoNacional(p)}", "OTRO" to "🌎 Pasaporte")
    }
}

/** [enlaceWhatsApp] con el país de la sede activa (mismo orden de parámetros). Perú: idéntico. */
fun enlaceWhatsAppSede(contacto: String?, texto: String? = null, pais: String? = paisActivo()): String? =
    enlaceWhatsAppPais(contacto, pais, texto)
