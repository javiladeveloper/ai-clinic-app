package pe.saniape.app.data.staff

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * PAÍS, MONEDA Y ZONA HORARIA POR SEDE (multipaís, Etapa 1). Gemelo de
 * lib/zona.ts + lib/multipais.ts de la web.
 *
 * Resolución (la misma que las funciones SQL moneda_de_sede/zona_de_sede):
 *     sede.X → (sin sede: la principal.X) → clínica.X → PE / PEN / America/Lima
 * La web ya resuelve cada sede en /api/staff/contexto: la app lee
 * [ContextoStaff.moneda]/[zona]/[pais] (de la clínica) y los de cada [SedeRef]
 * con [monedaDeSede] / [zonaDeSede].
 *
 * [ZONA_CLINICA] (ReglasFicha.kt) ahora sigue a la sede activa (Lima por
 * defecto): `hoyClinicaIso()` y sus usos no cambian.
 */

const val ZONA_POR_DEFECTO = "America/Lima"
const val PAIS_POR_DEFECTO = "PE"

/** Lima fija: el último recurso (nunca depende del contexto, sin recursión). */
internal val TZ_LIMA: TimeZone = runCatching { TimeZone.of(ZONA_POR_DEFECTO) }
    .getOrElse { TimeZone.currentSystemDefault() }

/** La TimeZone de un id IANA; null si el sistema no la conoce. */
private fun zonaONull(id: String?): TimeZone? {
    val z = id.orEmpty().trim()
    if (z.isEmpty()) return null
    if (z == ZONA_POR_DEFECTO) return TZ_LIMA
    return runCatching { TimeZone.of(z) }.getOrNull()
}

/** ¿El sistema conoce esta zona IANA? */
fun esZonaValida(id: String?): Boolean = zonaONull(id) != null

/** TimeZone de la zona dada; vacía o inválida → Lima. */
fun zonaDe(id: String?): TimeZone = zonaONull(id) ?: TZ_LIMA

/** "Hoy" (AAAA-MM-DD) en la zona dada. [ahora] inyectable para los tests. */
fun hoyEnIso(zona: String?, ahora: Instant = Clock.System.now()): String {
    val d = ahora.toLocalDateTime(zonaDe(zona)).date
    return "${d.year}-${d.monthNumber.toString().padStart(2, '0')}-${d.dayOfMonth.toString().padStart(2, '0')}"
}

/** "Ahora" en una zona (gemelo de `ahoraEn` de la web). */
data class AhoraEnZona(
    /** AAAA-MM-DD. */
    val fecha: String,
    /** "HH:MM" (24 h). */
    val hora: String,
    /** Minutos desde la medianoche de la zona. */
    val minutos: Int,
    /** 0 = domingo (como la web). */
    val diaIdx: Int,
    /** La zona efectivamente usada (inválida → America/Lima). */
    val zona: String,
)

fun ahoraEn(zona: String?, ahora: Instant = Clock.System.now()): AhoraEnZona {
    val usada = if (esZonaValida(zona)) zona!!.trim() else ZONA_POR_DEFECTO
    val t = ahora.toLocalDateTime(zonaDe(usada))
    return AhoraEnZona(
        fecha = hoyEnIso(usada, ahora),
        hora = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}",
        minutos = t.hour * 60 + t.minute,
        // DayOfWeek: MONDAY (ordinal 0) … SUNDAY (6) → 0 = domingo.
        diaIdx = (t.dayOfWeek.ordinal + 1) % 7,
        zona = usada,
    )
}

// ── Moneda/zona/país de una sede, desde el contexto ──

private fun ContextoStaff.sedeOPrincipal(sedeId: String?): SedeRef? {
    val id = sedeId?.takeIf { it.isNotBlank() }
    if (id != null) return sedes.firstOrNull { it.id == id }
    // Sin sede (consolidado / fila sin sede) = la principal.
    val principal = sedePrincipalId ?: sedes.firstOrNull { it.esPrincipal }?.id
    return principal?.let { p -> sedes.firstOrNull { it.id == p } }
}

/** Moneda ISO 4217 de una sede (sede → principal si null → clínica → PEN). */
fun ContextoStaff.monedaDeSede(sedeId: String?): String =
    sedeOPrincipal(sedeId)?.moneda?.takeIf { it.isNotBlank() }?.let(::normalizarMoneda) ?: moneda

/** Zona IANA de una sede (sede → principal si null → clínica → America/Lima). */
fun ContextoStaff.zonaDeSede(sedeId: String?): String =
    sedeOPrincipal(sedeId)?.zona?.takeIf { esZonaValida(it) } ?: zona

/** País ISO-2 de una sede (sede → principal si null → clínica → PE). */
fun ContextoStaff.paisDeSede(sedeId: String?): String =
    sedeOPrincipal(sedeId)?.pais?.takeIf { it.isNotBlank() } ?: pais

/** Monedas distintas entre la clínica y sus sedes (PEN primero). 2 o más = un total por moneda. */
val ContextoStaff.monedasEnUso: List<String>
    get() = ordenarMonedas(listOf(monedaDeSede(null)) + sedes.map { monedaDeSede(it.id) })

/**
 * La zona de la sede ACTIVA ahora mismo: contexto cargado + sede elegida
 * (SedeActiva). Sin contexto o sin multisede → la de la clínica / Lima.
 */
internal fun zonaActivaId(): String? {
    val ctx = StaffContextoRepo.actual ?: return null
    val sedeId = if (ctx.multiSede) SedeActiva.estado.value.sedeId else null
    return ctx.zonaDeSede(sedeId)
}

private var memoZona: Pair<String, TimeZone>? = null

/** TimeZone de la sede activa, memorizada (se lee en listas: no rehacer TimeZone.of por fila). */
internal fun zonaActiva(): TimeZone {
    val id = zonaActivaId() ?: return TZ_LIMA
    memoZona?.let { (k, tz) -> if (k == id) return tz }
    return zonaDe(id).also { memoZona = id to it }
}
