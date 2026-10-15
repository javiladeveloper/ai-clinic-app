package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * CALENDARIO DEL MES de la agenda (lógica pura, sin Compose ni red). Gemelo de
 * `buildCalendar` de /citas web: semana desde LUNES, días del mes anterior y
 * siguiente de relleno hasta completar semanas, "hoy" en la zona de la sede.
 * La web marca un punto si el día tiene citas; la app además cuenta cuántas y
 * de qué estados (con el mismo filtro que la agenda).
 */

/** Un mes concreto. [mes] 1..12. */
data class MesAgenda(val anio: Int, val mes: Int) {
    init { require(mes in 1..12) { "mes fuera de rango: $mes" } }

    fun siguiente(): MesAgenda = if (mes == 12) MesAgenda(anio + 1, 1) else MesAgenda(anio, mes + 1)
    fun anterior(): MesAgenda = if (mes == 1) MesAgenda(anio - 1, 12) else MesAgenda(anio, mes - 1)

    /** Primer día (AAAA-MM-01). */
    val desdeIso: String get() = isoDe(anio, mes, 1)
    /** Último día del mes (AAAA-MM-28..31). */
    val hastaIso: String get() = isoDe(anio, mes, diasDelMes(anio, mes))

    /** "Octubre 2026". */
    val titulo: String get() = "${MESES_ES[mes - 1].replaceFirstChar { it.uppercase() }} $anio"

    /** Clave estable ("2026-10") para la caché. */
    val clave: String get() = "$anio-${mes.toString().padStart(2, '0')}"

    companion object {
        /** El mes de una fecha AAAA-MM-DD (null si no se entiende). */
        fun de(iso: String?): MesAgenda? {
            val p = iso?.take(10)?.split("-") ?: return null
            if (p.size != 3) return null
            val a = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
            return MesAgenda(a, m)
        }
    }
}

val MESES_ES = listOf(
    "enero", "febrero", "marzo", "abril", "mayo", "junio",
    "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre",
)

/** Cabecera de la grilla, semana desde lunes (como la web). */
val DIAS_SEMANA_CORTOS = listOf("L", "M", "M", "J", "V", "S", "D")

private fun isoDe(a: Int, m: Int, d: Int) =
    "$a-${m.toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}"

/** Días del mes (28/29/30/31). */
fun diasDelMes(anio: Int, mes: Int): Int {
    val primero = LocalDate(anio, mes, 1)
    val sig = primero.plus(DatePeriod(months = 1))
    return (sig.toEpochDays() - primero.toEpochDays())
}

/** El mes de HOY en la zona dada (la de la sede), nunca UTC. */
fun mesDeHoy(zona: String?, ahora: Instant = kotlinx.datetime.Clock.System.now()): MesAgenda =
    MesAgenda.de(hoyEnIso(zona, ahora))!!

/** Una celda de la grilla. [delMes] = false para el relleno del mes anterior/siguiente. */
data class CeldaMes(
    val iso: String,
    val dia: Int,
    val delMes: Boolean,
    val esHoy: Boolean,
)

/**
 * La grilla del mes: semanas completas (múltiplo de 7), lunes primero. El
 * relleno lleva su fecha real (para pintarlo atenuado), pero no es del mes.
 * [hoy] AAAA-MM-DD ya en la zona de la sede.
 */
fun grillaMes(mes: MesAgenda, hoy: String): List<CeldaMes> {
    val primero = LocalDate(mes.anio, mes.mes, 1)
    // DayOfWeek.ordinal: MONDAY=0 … SUNDAY=6 → huecos antes del día 1.
    val huecos = primero.dayOfWeek.ordinal
    val n = diasDelMes(mes.anio, mes.mes)
    val total = ((huecos + n + 6) / 7) * 7
    val inicio = primero.plus(DatePeriod(days = -huecos))
    return (0 until total).map { i ->
        val f = inicio.plus(DatePeriod(days = i))
        val iso = isoDe(f.year, f.monthNumber, f.dayOfMonth)
        CeldaMes(
            iso = iso,
            dia = f.dayOfMonth,
            delMes = f.monthNumber == mes.mes && f.year == mes.anio,
            esHoy = iso == hoy,
        )
    }
}

/**
 * Fila LIVIANA de una cita para el conteo del mes: lo justo para contar y
 * aplicar los mismos filtros que la agenda (nunca el join pesado de la lista).
 */
data class FilaCitaMes(
    val fecha: String,
    val estado: String,
    val tipo: String? = null,
    val terapeutaId: String? = null,
    val especialidadId: String? = null,
    /** La del servicio del tratamiento (solo se pide con el filtro de especialidad puesto). */
    val especialidadServicioId: String? = null,
)

/** Conteo de un día: [total] = citas que cuentan; [porEstado] para los puntos de color. */
data class ConteoDia(val total: Int, val porEstado: Map<String, Int>) {
    /** Estados presentes en el orden en que se pintan los puntos (máx. 3). */
    val estadosPuntos: List<String>
        get() = ORDEN_ESTADOS_PUNTOS.filter { (porEstado[it] ?: 0) > 0 }
            .plus(porEstado.keys.filter { it !in ORDEN_ESTADOS_PUNTOS && (porEstado[it] ?: 0) > 0 }.sorted())
            .take(3)
}

/** Lo pendiente primero (lo que falta atender), luego lo hecho, luego lo caído. */
val ORDEN_ESTADOS_PUNTOS = listOf("Pendiente", "Confirmada", "Completada", "No asistió", "Cancelada")

/**
 * Conteo por día. [pasa] = los filtros de la agenda (estado, tipo, profesional,
 * especialidad). Las CANCELADAS no suman al número (ya no ocupan la agenda),
 * salvo que se esté filtrando justo por "Cancelada"; sí dejan su punto rojo.
 */
fun conteosPorDia(
    filas: List<FilaCitaMes>,
    filtroEstado: String? = null,
    pasa: (FilaCitaMes) -> Boolean = { true },
): Map<String, ConteoDia> {
    val cuentaCanceladas = filtroEstado == "Cancelada"
    return filas.asSequence()
        .filter { it.fecha.length >= 10 }
        .filter { filtroEstado == null || it.estado == filtroEstado }
        .filter(pasa)
        .groupBy { it.fecha.take(10) }
        .mapValues { (_, fs) ->
            ConteoDia(
                total = fs.count { cuentaCanceladas || it.estado != "Cancelada" },
                porEstado = fs.groupingBy { it.estado }.eachCount(),
            )
        }
}

/** "15 de octubre, 3 citas" (lectores de pantalla). Hoy y elegido se anuncian. */
fun descripcionDia(iso: String, conteo: ConteoDia?, esHoy: Boolean = false, elegido: Boolean = false): String {
    val mes = MesAgenda.de(iso)
    val dia = iso.take(10).split("-").getOrNull(2)?.toIntOrNull()
    val fecha = if (mes != null && dia != null) "$dia de ${MESES_ES[mes.mes - 1]}" else iso
    val n = conteo?.total ?: 0
    val citas = when (n) { 0 -> "sin citas"; 1 -> "1 cita"; else -> "$n citas" }
    return buildString {
        append(fecha)
        if (esHoy) append(", hoy")
        append(", ").append(citas)
        if (elegido) append(", seleccionado")
    }
}

/**
 * Base de la tira de días: alrededor de HOY (-2..+12, como siempre) si el día
 * elegido cae ahí; si se saltó a otro día desde el calendario, alrededor de ese.
 */
fun baseTiraDias(hoy: String, seleccionado: String, antes: Int = 2, despues: Int = 12): String {
    val h = runCatching { LocalDate.parse(hoy) }.getOrNull() ?: return hoy
    val s = runCatching { LocalDate.parse(seleccionado) }.getOrNull() ?: return hoy
    val dif = s.toEpochDays() - h.toEpochDays()
    return if (dif in -antes..despues) hoy else seleccionado
}
