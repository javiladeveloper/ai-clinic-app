package pe.saniape.app.data.staff

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

/**
 * "15 de octubre, 3 citas" (lectores de pantalla). Hoy y elegido se anuncian.
 * Un día con solo canceladas no es "sin citas": "2 citas canceladas".
 */
fun descripcionDia(iso: String, conteo: ConteoDia?, esHoy: Boolean = false, elegido: Boolean = false): String {
    val mes = MesAgenda.de(iso)
    val dia = iso.take(10).split("-").getOrNull(2)?.toIntOrNull()
    val fecha = if (mes != null && dia != null) "$dia de ${MESES_ES[mes.mes - 1]}" else iso
    val n = conteo?.total ?: 0
    val canceladas = conteo?.porEstado?.get("Cancelada") ?: 0
    val citas = when {
        n == 0 && canceladas == 1 -> "1 cita cancelada"
        n == 0 && canceladas > 1 -> "$canceladas citas canceladas"
        n == 0 -> "sin citas"
        n == 1 -> "1 cita"
        else -> "$n citas"
    }
    return buildString {
        append(fecha)
        if (esHoy) append(", hoy")
        append(", ").append(citas)
        // Con citas activas, las canceladas se mencionan aparte (no suman al número).
        if (n > 0 && canceladas > 0) append(if (canceladas == 1) ", 1 cancelada" else ", $canceladas canceladas")
        if (elegido) append(", seleccionado")
    }
}

/**
 * Base de la tira de días (-2..+12 alrededor de ella). Empieza en HOY y NO se
 * mueve mientras el día elegido siga dentro de la tira (tocar un día de la tira
 * se comporta como siempre). Solo si se saltó fuera (calendario, aviso de
 * cita, "Ver día") se arma alrededor del día elegido.
 */
fun baseTiraDias(baseActual: String, seleccionado: String, antes: Int = 2, despues: Int = 12): String {
    val b = runCatching { LocalDate.parse(baseActual) }.getOrNull() ?: return baseActual
    val s = runCatching { LocalDate.parse(seleccionado) }.getOrNull() ?: return baseActual
    val dif = s.toEpochDays() - b.toEpochDays()
    return if (dif in -antes..despues) baseActual else seleccionado
}

// ── Lectura del mes: filas, paginado y caché ──

/** Fila liviana desde el JSON de PostgREST (tolerante: lo que falte queda null/vacío). */
fun filaCitaMesDeJson(o: JsonObject): FilaCitaMes {
    fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
    fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
    return FilaCitaMes(
        fecha = o.str("fecha").orEmpty(),
        estado = o.str("estado").orEmpty(),
        tipo = o.str("tipo"),
        terapeutaId = o.str("terapeuta_id"),
        especialidadId = o.str("especialidad_id"),
        especialidadServicioId = o.obj("tratamiento")?.obj("procedimiento")?.str("especialidad_id"),
    )
}

/** Lo leído de un mes. [llegoAlTope] = se cortó en el tope: el conteo es parcial. */
data class MesLeido(val filas: List<FilaCitaMes>, val llegoAlTope: Boolean = false)

/** Resultado de [paginarHastaVacia]. */
data class Paginado<T>(val filas: List<T>, val llegoAlTope: Boolean)

/**
 * Pide en tandas hasta recibir una VACÍA (no "una tanda corta": si el servidor
 * tiene un `max_rows` menor que [tanda], una tanda corta no significa el final).
 * Avanza por lo que realmente llegó. Se detiene en [tope] filas y lo dice
 * ([Paginado.llegoAlTope]) para avisar, en vez de cortar en silencio.
 */
suspend fun <T> paginarHastaVacia(
    tanda: Int,
    tope: Int,
    traer: suspend (desde: Long, hasta: Long) -> List<T>,
): Paginado<T> {
    require(tanda > 0 && tope > 0)
    val out = ArrayList<T>()
    var desde = 0L
    while (out.size < tope) {
        val filas = traer(desde, desde + tanda - 1)
        if (filas.isEmpty()) return Paginado(out, llegoAlTope = false)
        out.addAll(filas)
        desde += filas.size
    }
    return Paginado(out.take(tope), llegoAlTope = true)
}

/**
 * Caché por mes del calendario (sin Compose ni corrutinas: se prueba sola).
 * Claves: "AAAA-MM" + sufijos de alcance que empiezan con ":" (profesional,
 * sede, con servicio). Reglas:
 *  - Un mes FRESCO no se vuelve a pedir; uno VENCIDO se sigue mostrando
 *    mientras se relee (la grilla no se vacía).
 *  - Cada pedido lleva un turno: el resultado de un pedido viejo (cancelado o
 *    reemplazado) no pisa el del nuevo.
 *  - Si el mes vence MIENTRAS se lee, lo leído se guarda pero queda vencido
 *    (pudo leerse antes del cambio) y se vuelve a pedir.
 */
class CacheMesAgenda<T> {
    private val datos = HashMap<String, T>()
    private val vencidos = HashSet<String>()
    private val enCurso = HashMap<String, Int>()
    private val repedir = HashSet<String>()
    private var turnos = 0

    fun datos(clave: String): T? = datos[clave]
    fun fresco(clave: String): Boolean = clave in datos && clave !in vencidos
    fun enCurso(clave: String): Boolean = clave in enCurso

    /** Empieza un pedido de [clave]; devuelve su turno (reemplaza al anterior de la misma clave). */
    fun empezar(clave: String): Int {
        val t = ++turnos
        enCurso[clave] = t
        repedir.remove(clave)
        return t
    }

    /**
     * Fin del pedido [turno] de [clave]: [valor] null = falló o se canceló. Un
     * turno viejo se ignora (devuelve false). true = el mes quedó fresco.
     */
    fun terminar(clave: String, turno: Int, valor: T?): Boolean {
        if (enCurso[clave] != turno) return false
        enCurso.remove(clave)
        if (valor != null) {
            datos[clave] = valor
            if (clave in repedir) vencidos.add(clave) else vencidos.remove(clave)
        }
        repedir.remove(clave)
        return fresco(clave)
    }

    /** Vence los meses dados ("AAAA-MM"); null = todos. */
    fun vencer(meses: Set<String>?) {
        fun afecta(k: String) = meses == null || meses.any { k == it || k.startsWith("$it:") }
        datos.keys.filter(::afecta).forEach { vencidos.add(it) }
        enCurso.keys.filter(::afecta).forEach { repedir.add(it) }
    }
}

/**
 * Meses tocados por un cambio de Realtime: el de la fila nueva y el de la vieja
 * (reprogramar puede cambiar de mes). null = no se sabe (p. ej. un DELETE sin la
 * fila completa): hay que vencer todos.
 */
fun mesesDeCambio(fechaNueva: String?, fechaVieja: String?): Set<String>? =
    listOfNotNull(MesAgenda.de(fechaNueva), MesAgenda.de(fechaVieja)).map { it.clave }.toSet().ifEmpty { null }

/** Junta lo pendiente de vencer: null (= todos) absorbe cualquier otra cosa. */
fun unirMeses(a: Set<String>?, b: Set<String>?): Set<String>? = if (a == null || b == null) null else a + b
