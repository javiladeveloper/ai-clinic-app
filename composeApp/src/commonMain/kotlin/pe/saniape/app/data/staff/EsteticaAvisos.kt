package pe.saniape.app.data.staff

/**
 * AVISOS DEL FLUJO DE ESTÉTICA (23/11/2026). Lógica PURA, gemela de la web:
 * `lib/aplicaciones-esteticas.ts` (control tras el mapa, mapa suelto),
 * `lib/alertas-estetica.ts` (franja de fototipo y contraindicaciones),
 * `lib/tratamiento-sin-alta.ts` («falta dar de alta») y `lib/fotos-paciente.ts`.
 */

/** Los plazos que se ofrecen para el control tras una aplicación (días). */
val DIAS_CONTROL_APLICACION = listOf(7, 15, 30)

/**
 * El plazo resaltado: 15 días con toxina o relleno (el retoque); 30 con
 * bioestimulador o regenerativo solos (su efecto tarda más).
 */
fun diasControlSugerido(tiposProducto: List<String>): Int {
    val tipos = tiposProducto.toSet()
    if ("toxina" in tipos || "acido_hialuronico" in tipos) return 15
    if ("bioestimulador" in tipos || "regenerativo" in tipos) return 30
    return 15
}

/** El aviso del mapa NUEVO sin cita ni tratamiento; null = nada que avisar. */
fun avisoMapaSuelto(nuevo: Boolean, citaId: String?, tratamientoId: String?, hayCitas: Boolean, hayTratamientos: Boolean): String? {
    if (!nuevo || !citaId.isNullOrBlank() || !tratamientoId.isNullOrBlank()) return null
    val inicio = "Este mapa quedará sin cita ni tratamiento: se guarda en el historial, pero no se podrá cobrar desde aquí."
    return when {
        hayTratamientos -> "$inicio Elige su tratamiento arriba."
        hayCitas -> "$inicio Elige su cita arriba."
        else -> "$inicio Si es una atención de hoy, crea primero la cita o el tratamiento."
    }
}

// ── Franja de fototipo y contraindicaciones ─────────────────────────────────

enum class TonoAlerta { INFO, AVISO, RIESGO }
data class AlertaEstetica(val clave: String, val icono: String, val texto: String, val tono: TonoAlerta)

private val TILDES = mapOf('á' to 'a', 'é' to 'e', 'í' to 'i', 'ó' to 'o', 'ú' to 'u', 'ü' to 'u', 'ñ' to 'n')
private fun plano(s: String?): String = s.orEmpty().lowercase().map { TILDES[it] ?: it }.joinToString("").trim()

private val NEGACION = Regex("""^(no|ninguno|ninguna|nunca|na|sin|niega|negativo|0)\b""")
private val SOLO_SIGNOS = Regex("""^[-—–.\s]+$""")

/** ¿La respuesta es un «sí»? «true», «sí», o un texto que no sea una negación. */
fun respuestaAfirmativa(v: String?): Boolean {
    val t = plano(v)
    if (t.isEmpty() || t == "false") return false
    if (SOLO_SIGNOS.matches(t) || t == "n/a") return false
    return !NEGACION.containsMatchIn(t)
}

private class ReglaAlerta(val clave: String, val nombre: Regex, val icono: String, val tono: TonoAlerta, val texto: (String?) -> String?)

private fun siAfirma(texto: String): (String?) -> String? = { v -> if (respuestaAfirmativa(v)) texto else null }

private val REGLAS_ALERTA = listOf(
    ReglaAlerta("fototipo", Regex("fototipo|fitzpatrick"), "🎨", TonoAlerta.INFO) { v ->
        val t = v.orEmpty().trim()
        if (t.isEmpty()) null else "Fototipo " + t.replace(Regex("""^fototipo\s*""", RegexOption.IGNORE_CASE), "")
    },
    ReglaAlerta("embarazo", Regex("embaraz|lactan|gestan"), "🤰", TonoAlerta.RIESGO, siAfirma("Embarazo o lactancia")),
    ReglaAlerta("retinoides", Regex("retinoid|isotretino|roacut"), "💊", TonoAlerta.RIESGO, siAfirma("Usa retinoides")),
    ReglaAlerta("anestesia", Regex("anestes"), "⚠", TonoAlerta.RIESGO, siAfirma("Alergia a anestésicos")),
    ReglaAlerta("sol", Regex("exposicion solar|bronce|sol reciente"), "☀", TonoAlerta.AVISO, siAfirma("Exposición solar reciente")),
    ReglaAlerta("anticoagulantes", Regex("anticoagul|aspirina"), "🩸", TonoAlerta.AVISO, siAfirma("Toma anticoagulantes")),
    ReglaAlerta("queloides", Regex("queloid"), "⚠", TonoAlerta.AVISO, siAfirma("Tendencia a queloides")),
)

/**
 * Las alertas de la cabecera. [campos]: (id, nombre) de los campos propios
 * ACTIVOS de la clínica; [valores]: `pacientes.campos_custom`. Cada regla sale
 * una sola vez, en orden: primero el fototipo, luego lo que contraindica.
 */
fun alertasEstetica(
    campos: List<Pair<String, String>>, valores: Map<String, String>,
    /** El fototipo de la escala Fitzpatrick aplicada: si existe, manda sobre el campo. */
    fototipoEscala: String? = null,
): List<AlertaEstetica> {
    val out = mutableListOf<AlertaEstetica>()
    val deEscala = fototipoEscala.orEmpty().trim()
    if (deEscala.isNotEmpty()) {
        out += AlertaEstetica("fototipo", "🎨", "Fototipo " + deEscala.replace(Regex("^fototipo\\s*", RegexOption.IGNORE_CASE), ""), TonoAlerta.INFO)
    }
    for (r in REGLAS_ALERTA) {
        if (r.clave == "fototipo" && deEscala.isNotEmpty()) continue
        for ((id, nombre) in campos) {
            if (!r.nombre.containsMatchIn(plano(nombre))) continue
            val texto = r.texto(valores[id]) ?: continue
            out += AlertaEstetica(r.clave, r.icono, texto, r.tono)
            break
        }
    }
    return out
}

/** ¿Esta escala aplicada es la del fototipo? Por su nombre (de fábrica o propia de la clínica). */
fun esEscalaFototipo(nombre: String?, corto: String?): Boolean =
    Regex("fitzpatrick|fototipo").containsMatchIn(plano("${nombre.orEmpty()} ${corto.orEmpty()}"))

// ── El control cuando el tratamiento ya terminó ─────────────────────────────

/** Días del control que se propone tras un procedimiento de estética (el retoque). */
const val DIAS_CONTROL_TRAS_CIERRE = 15

/**
 * ¿El servidor no propuso la próxima cita PORQUE el tratamiento terminó? Solo
 * entonces cabe ofrecer el control (gemelo de `ofreceControlTrasCierre`).
 */
fun ofreceControlTrasCierre(motivo: String?): Boolean = motivo == "tratamiento_cerrado" || motivo == "plan_terminado"

// ── «Tratamiento terminado · falta dar de alta» ─────────────────────────────

/** El valor del filtro de la lista de pacientes (no es un estado de la base). */
const val FILTRO_SIN_ALTA = "sin_alta"
const val ETIQUETA_SIN_ALTA = "Terminaron sin alta"

/**
 * El tratamiento terminado que espera el alta (el más reciente), o null: el
 * paciente no figura «En tratamiento», tiene algo en curso (o suspendido) o no
 * tiene ninguno completado. [tratamientos]: (id, estado, fecha de inicio).
 */
fun tratamientoPendienteDeAlta(estadoPaciente: String?, tratamientos: List<Triple<String, String?, String?>>): String? {
    if (estadoPaciente != "En tratamiento") return null
    if (tratamientos.any { it.second == "Activo" || it.second == "Suspendido" }) return null
    return tratamientos.filter { it.second == "Completado" }.maxByOrNull { it.third.orEmpty() }?.first
}

// ── Pestaña 📷 Fotos ─────────────────────────────────────────────────────────

/** Cuántos tratamientos terminados recientes se muestran además de los en curso. */
const val RECIENTES_EN_FOTOS = 3

/**
 * Los tratamientos de la pestaña 📷 Fotos: los en curso y los más recientes
 * aunque hayan terminado (la foto «después» se toma en el control). Orden: el
 * más reciente primero. [tratamientos]: (id, estado, fecha de inicio).
 */
fun tratamientosParaFotos(tratamientos: List<Triple<String, String?, String?>>): List<String> {
    val vivos = tratamientos.filter { it.second != "Eliminado" }.sortedByDescending { it.third.orEmpty() }
    val recientes = vivos.take(RECIENTES_EN_FOTOS).map { it.first }.toSet()
    return vivos.filter { it.second == "Activo" || it.first in recientes }.map { it.first }
}
