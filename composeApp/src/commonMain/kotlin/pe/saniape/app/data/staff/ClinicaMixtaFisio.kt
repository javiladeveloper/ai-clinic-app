package pe.saniape.app.data.staff

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Mejoras propias de FISIOTERAPIA (auditoría fisio 2026-09-25: M1 EVA, M2
 * mejorías, M3 renovación, M4 "No volvió", M10 faltas sin aviso).
 *
 * Mismo criterio que lo dental (ver `ClinicaMixtaDental.kt`): se decide POR
 * CITA / TRATAMIENTO, nunca por clínica. En una clínica mixta el odontólogo no
 * ve la escala del dolor y el fisio sí. El MAPA lo arma la web
 * (/api/staff/contexto: `especialidadesFisio`, `soloFisio`); un backend sin esos
 * campos deja el mapa vacío y NADA de esto aparece (la app se comporta como antes).
 *
 * Gemelos de `mapaFisio`/`citaEsFisio` (lib/flujo.ts), `lib/cierre-sesion.ts`,
 * `lib/renovacion.ts`, `lib/no-volvio.ts` y `lib/faltas-paciente.ts`.
 */
data class MapaFisio(val ids: List<String> = emptyList(), val solo: Boolean = false) {
    /** ¿La clínica hace algo de fisioterapia? */
    val activo: Boolean get() = solo || ids.isNotEmpty()
}

/**
 * ¿Esta cita / tratamiento es de fisioterapia? Especialidad de la cita → la del
 * servicio → el profesional (si TODAS sus especialidades son de fisio). Lo que
 * no se sabe es NO.
 */
fun citaEsFisio(
    mapa: MapaFisio,
    especialidadId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
): Boolean {
    if (mapa.solo) return true
    if (mapa.ids.isEmpty()) return false
    listOf(especialidadId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
        ?.let { return it in mapa.ids }
    val prof = especialidadesProfesional.orEmpty().filter { it.isNotBlank() }
    return prof.isNotEmpty() && prof.all { it in mapa.ids }
}

// ─── EVA (escala visual analógica del dolor, 0–10) ──────────────────────────

val EVA_VALORES: List<Int> = (0..10).toList()

/**
 * Color de cada valor de la escala (verde → ámbar → rojo), mismos anclajes que
 * la web (#16a34a, #d97706, #dc2626). Devuelve ARGB (0xFFrrggbb).
 */
fun colorEvaArgb(n: Int): Long {
    val anclas = listOf(intArrayOf(22, 163, 74), intArrayOf(217, 119, 6), intArrayOf(220, 38, 38))
    val t = n.coerceIn(0, 10) / 10.0
    val (a, b, f) = if (t <= 0.5) Triple(anclas[0], anclas[1], t / 0.5) else Triple(anclas[1], anclas[2], (t - 0.5) / 0.5)
    val rgb = IntArray(3) { i -> (a[i] + (b[i] - a[i]) * f).roundToInt() }
    return 0xFF000000L or (rgb[0].toLong() shl 16) or (rgb[1].toLong() shl 8) or rgb[2].toLong()
}

/** Etiqueta corta: "EVA 7 → 3", "EVA 7 →", "EVA → 3" o "". */
fun textoEva(inicio: Int?, fin: Int?): String {
    if (inicio == null && fin == null) return ""
    return "EVA ${inicio ?: ""} → ${fin ?: ""}".replace(Regex("\\s+"), " ").trim()
}

data class PuntoDolor(val numero: Int, val fecha: String, val inicio: Int?, val fin: Int?)

/** Sesiones COMPLETADAS con algún valor EVA, por número. Vacío = no se grafica. */
fun curvaDolor(sesiones: List<SesionFicha>): List<PuntoDolor> =
    sesiones
        .filter { it.estado == "Completada" && (it.dolorInicio != null || it.dolorFin != null) }
        .map { PuntoDolor(it.numero, it.fecha.take(10), it.dolorInicio, it.dolorFin) }
        .sortedBy { it.numero }

data class ResumenDolor(val desde: Int, val hasta: Int) { val cambio: Int get() = hasta - desde }

/** Primer dolor de entrada contra el último de salida. */
fun resumenDolor(puntos: List<PuntoDolor>): ResumenDolor? {
    val primero = puntos.firstOrNull { it.inicio != null } ?: puntos.firstOrNull { it.fin != null }
    val ultimo = puntos.lastOrNull { it.fin != null } ?: puntos.lastOrNull { it.inicio != null }
    if (primero == null || ultimo == null) return null
    return ResumenDolor(primero.inicio ?: primero.fin!!, ultimo.fin ?: ultimo.inicio!!)
}

// ─── Mejorías ────────────────────────────────────────────────────────────────

/** Chips de evolución (fisio). Gemelo de `MEJORIAS_RAPIDAS`. */
val MEJORIAS_RAPIDAS: List<String> = listOf("Menos dolor", "Más movilidad", "Menos rigidez", "Sin cambios", "Peor")

private fun partesMejoria(texto: String): List<String> = texto.split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun tieneMejoria(texto: String, chip: String): Boolean =
    partesMejoria(texto).any { TecnicasNormalizar.clave(it) == TecnicasNormalizar.clave(chip) }

/** Toca un chip: si ya está lo quita, si no lo agrega al final ("A, B"). */
fun alternarMejoria(texto: String, chip: String): String {
    val partes = partesMejoria(texto)
    val k = TecnicasNormalizar.clave(chip)
    return if (partes.any { TecnicasNormalizar.clave(it) == k }) partes.filter { TecnicasNormalizar.clave(it) != k }.joinToString(", ")
    else (partes + chip).joinToString(", ")
}

// ─── Dictado ─────────────────────────────────────────────────────────────────

/** Suma un fragmento dictado a un texto libre (mejorías). */
fun unirDictado(previo: String, fragmento: String): String {
    val f = fragmento.trim()
    if (f.isEmpty()) return previo
    return if (previo.isNotBlank()) "${previo.trimEnd()} $f" else f
}

/**
 * Lo dictado como técnicas ("A + B"): las conjunciones de enumeración ("y",
 * "más", "luego"…) separan; el normalizador del catálogo hace el resto. Lo que ya
 * estaba no se duplica.
 */
fun sumarTecnicasDictadas(previo: String, fragmento: String): String {
    val enLista = fragmento.replace(Regex("\\s+(?:y|e|más|mas|luego|después|despues)\\s+", RegexOption.IGNORE_CASE), " + ")
    val nuevas = TecnicasNormalizar.partir(enLista)
    if (nuevas.isEmpty()) return previo
    val actuales = previo.split(" + ").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    for (t in nuevas) if (actuales.none { TecnicasNormalizar.clave(it) == TecnicasNormalizar.clave(t) }) actuales.add(t)
    return actuales.joinToString(" + ")
}

// ─── Renovación (M3) ─────────────────────────────────────────────────────────

/** Con cuántas sesiones restantes se avisa. */
const val UMBRAL_RENOVACION = 2

/** ¿Plan de sesiones? (Paquete o Sesión suelta con sesiones prescritas.) */
fun esPlanDeSesiones(modalidad: String?, totalSesiones: Int): Boolean =
    modalidad != "Consulta" && modalidad != "Unidades" && totalSesiones > 0

/** Cuántas quedan (1..UMBRAL) para ofrecer renovar, o null. Solo Activos por sesiones. */
fun avisoRenovacion(estado: String?, modalidad: String?, totalSesiones: Int, completadas: Int): Int? {
    if (estado != "Activo" || !esPlanDeSesiones(modalidad, totalSesiones)) return null
    val r = max(0, totalSesiones - completadas)
    return if (r in 1..UMBRAL_RENOVACION) r else null
}

fun textoAvisoRenovacion(restantes: Int): String =
    if (restantes == 1) "Le queda 1 sesión" else "Le quedan $restantes sesiones"

// ─── No volvió (M4) ──────────────────────────────────────────────────────────

data class MotivoNoVolvio(val id: String, val label: String)

val MOTIVOS_NO_VOLVIO: List<MotivoNoVolvio> = listOf(
    MotivoNoVolvio("no_contesta", "No contesta"),
    MotivoNoVolvio("se_mudo", "Se mudó"),
    MotivoNoVolvio("mejoro", "Mejoró y no volvió"),
    MotivoNoVolvio("economico", "Motivo económico"),
    MotivoNoVolvio("otro", "Otro"),
)

/** Texto de `motivo_cierre`; null si el motivo no es válido ("Otro" exige detalle). */
fun textoMotivoCierre(motivo: String, detalle: String?): String? {
    val m = MOTIVOS_NO_VOLVIO.firstOrNull { it.id == motivo } ?: return null
    val d = detalle.orEmpty().trim().take(300)
    if (m.id == "otro" && d.isEmpty()) return null
    return if (d.isNotEmpty()) "${m.label} — $d" else m.label
}

/** ¿Admite "No volvió"? Solo por sesiones, Activo y con sesiones prescritas. */
fun puedeMarcarNoVolvio(estado: String?, modalidad: String?, totalSesiones: Int): Boolean {
    if (estado != "Activo") return false
    if (modalidad == "Consulta" || modalidad == "Unidades") return false
    return totalSesiones > 0
}

/** ¿Se cerró por abandono? */
fun esNoVolvio(estado: String?, noVolvio: Boolean): Boolean = estado == "Suspendido" && noVolvio

// ─── Faltas sin aviso (M10) ──────────────────────────────────────────────────

/** Nota que deja el cron al cerrar una cita vieja sin gestionar (lib/faltas-paciente.ts). */
const val NOTA_NO_ASISTIO = "No asistió (cierre automático por antigüedad)"

/** ¿Falta SIN aviso? (`citas.no_asistio`, o el cierre automático "No asistió"). */
fun esFaltaSinAviso(estado: String?, notas: String?, noAsistio: Boolean): Boolean {
    if (noAsistio) return true
    return estado == "Cancelada" && notas?.contains(NOTA_NO_ASISTIO) == true
}
