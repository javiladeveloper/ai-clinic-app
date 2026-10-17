package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.Supabase

// ─────────────────────────────────────────────────────────────────────────────
// EL CONTROL CAE EN UN DÍA QUE EL PROFESIONAL ATIENDE (2026-10-16) — GEMELO de
// `franjasParaControl` / `fechaControlConHorario` de lib/consulta-guiada.ts.
//
// "en 30 días" desde una cita de un jueves propone un sábado; si el profesional
// no atiende ese día (o no a esa hora: el sábado suele ser solo de mañana), la
// agenda lo rechaza con "sin cupo". La fecha sugerida se corre al primer día con
// una franja ACTIVA de su horario, en la sede de la cita (misma regla que la
// agenda: la franja sin sede es de la principal), que contenga la hora del
// control; si ningún día de las dos semanas siguientes la tiene, al primero que
// atienda (la hora se cambia a mano).
//  · Sin ninguna fila de horario: el horario con el que nacen los profesionales
//    (horarioTerapeutaPorDefecto: L–V 08–18, Sáb 09–13).
//  · Con filas pero ninguna activa en esa sede: "no atiende" → la fecha de siempre.
//  · A demanda (horario_flexible): la fecha de siempre.
//  · Horario aún no leído (null): la fecha de siempre.
// El cupo (citas ya tomadas) no se mira aquí: lo valida la agenda al guardar.
// ─────────────────────────────────────────────────────────────────────────────

/** Una fila de `horarios_terapeuta` (dia corto: "Lun"…"Dom"). */
data class FranjaHorarioControl(
    val dia: String,
    val horaInicio: String,
    val horaFin: String,
    val activo: Boolean? = true,
    val sedeId: String? = null,
)

/** Atajos del próximo control ("en N días"), contados desde la fecha de la cita. */
val ATAJOS_CONTROL_DIAS = listOf(3, 7, 15, 30)
/** Fecha que el formulario de control propone de entrada. */
const val DIAS_CONTROL_POR_DEFECTO = 7

/**
 * El horario con el que nace un profesional que no trae uno (gemelo de
 * `horarioTerapeutaPorDefecto` de lib/horario-terapeuta-defecto.ts, que sale
 * de HORARIO_ATENCION_DEFAULT: L–V 08–18, Sáb 09–13).
 */
fun horarioTerapeutaPorDefecto(): List<FranjaHorarioControl> =
    listOf("Lun", "Mar", "Mié", "Jue", "Vie").map { FranjaHorarioControl(it, "08:00", "18:00", true) } +
        FranjaHorarioControl("Sáb", "09:00", "13:00", true)

/** ¿La franja cae en la sede? sin sede pedida = todas; la franja sin sede es de la principal (enSede de la web). */
private fun franjaEnSede(f: FranjaHorarioControl, sedeId: String?, principalId: String?): Boolean {
    if (sedeId.isNullOrEmpty()) return true
    val s = f.sedeId
    if (s == sedeId) return true
    return s == null && !principalId.isNullOrEmpty() && sedeId == principalId
}

/**
 * Las franjas con que se calcula el control: las activas de la sede de la cita;
 * sin ninguna fila, el horario por defecto; null = no se sabe.
 */
fun franjasParaControl(filas: List<FranjaHorarioControl>?, sedeId: String? = null, principalId: String? = null): List<FranjaHorarioControl>? {
    if (filas == null) return null
    if (filas.isEmpty()) return horarioTerapeutaPorDefecto()
    return filas.filter { it.activo != false && franjaEnSede(it, sedeId, principalId) }
}

/** Fecha del control "en N días" (desde la cita) corrida al primer día (y hora) que el profesional atiende. */
fun fechaControlConHorario(
    citaFecha: String,
    dias: Int,
    hora: String?,
    filas: List<FranjaHorarioControl>?,
    flexible: Boolean = false,
    sedeId: String? = null,
    principalId: String? = null,
): String {
    val base = sumarDiasIso(citaFecha, dias)
    if (flexible) return base
    val usar = franjasParaControl(filas, sedeId, principalId)
    if (usar.isNullOrEmpty()) return base
    val hhmm = hora.orEmpty().take(5)
    fun delDia(f: String) = diaCorto(f)?.let { d -> usar.filter { it.dia == d } }.orEmpty()
    fun cubre(x: FranjaHorarioControl) = hhmm.isEmpty() || (x.horaInicio.take(5) <= hhmm && hhmm < x.horaFin.take(5))
    for (i in 0 until 14) {
        val f = sumarDiasIso(base, i)
        if (delDia(f).any { cubre(it) }) return f
    }
    for (i in 0 until 14) {
        val f = sumarDiasIso(base, i)
        if (delDia(f).isNotEmpty()) return f
    }
    return base
}

/** El horario de quien atiende el control: sus franjas y si atiende a demanda. */
data class HorarioControl(val filas: List<FranjaHorarioControl>, val flexible: Boolean)

object HorarioControlRepo {
    /**
     * Una lectura chica (como PlanAtencion.tsx): franjas + horario_flexible en
     * paralelo. null = no se pudo (sin red): se usa la fecha de siempre.
     */
    suspend fun cargar(terapeutaId: String): HorarioControl? = runCatching {
        coroutineScope {
            val h = async {
                Supabase.client.postgrest["horarios_terapeuta"]
                    .select(Columns.list("dia", "hora_inicio", "hora_fin", "activo", "sede_id")) { filter { eq("terapeuta_id", terapeutaId) } }
                    .decodeList<JsonObject>()
            }
            val t = async {
                Supabase.client.postgrest["terapeutas"]
                    .select(Columns.list("horario_flexible")) { filter { eq("id", terapeutaId) } }
                    .decodeList<JsonObject>().firstOrNull()
            }
            val filas = h.await().mapNotNull { o ->
                val dia = o.txt("dia") ?: return@mapNotNull null
                val ini = o.txt("hora_inicio") ?: return@mapNotNull null
                val fin = o.txt("hora_fin") ?: return@mapNotNull null
                FranjaHorarioControl(dia, ini, fin, o.txt("activo")?.let { it == "true" } ?: true, o.txt("sede_id"))
            }
            HorarioControl(filas, t.await()?.txt("horario_flexible") == "true")
        }
    }.getOrNull()

    private fun JsonObject.txt(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
}
