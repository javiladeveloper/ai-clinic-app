package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 📞 QUIÉN AGENDÓ y 🧮 CIERRES DE CAJA (24/11/2026), las dos pestañas nuevas de Reportes.
 * Gemelos de lib/citas-agendador.ts y lib/cierres-reporte.ts (web). Los conteos de «Quién
 * agendó» los suma la base (RPC `citas_por_agendador`); los cierres se leen de `cierres_caja`.
 */

// ── Periodo ─────────────────────────────────────────────────────────────────

data class Periodo(val desde: String, val hasta: String)

/** Del 1 del mes a hoy. */
fun rangoDelMes(hoy: String): Periodo = Periodo(hoy.take(8) + "01", hoy)

/** El mes anterior completo. */
fun rangoMesAnterior(hoy: String): Periodo {
    val primero = LocalDate.parse(hoy.take(8) + "01")
    val ini = primero.minus(DatePeriod(months = 1))
    val fin = primero.minus(DatePeriod(days = 1))
    return Periodo(ini.toString(), fin.toString())
}

// ── Quién agendó ────────────────────────────────────────────────────────────

data class FilaAgendador(
    /** null = la creó el servidor (Sani, reserva en línea) o es anterior al registro. */
    val id: String?,
    val nombre: String,
    val rol: String,
    val agendadas: Int,
    val asistieron: Int,
    val noAsistieron: Int,
    val canceladas: Int,
    val porVenir: Int,
    val pacientesNuevos: Int,
    /** asistieron / (asistieron + no asistieron), 0–100; null si ninguna llegó a su fecha. */
    val asistencia: Int?,
)

const val SIN_PERSONA = "Sani, reserva en línea o sin registro"

fun porcentajeAsistencia(asistieron: Int, noAsistieron: Int): Int? {
    val base = asistieron + noAsistieron
    return if (base > 0) (asistieron * 100.0 / base).roundToInt() else null
}

private fun JsonObject.texto(k: String) = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
private fun JsonObject.entero(k: String) = texto(k)?.toDoubleOrNull()?.takeIf { it > 0 }?.toInt() ?: 0
private fun JsonObject.decimal(k: String) = texto(k)?.toDoubleOrNull()?.let { (it * 100).roundToInt() / 100.0 }

/** Las filas de la función → las del reporte: primero quien más agendó; lo sin persona, al final. */
fun filasAgendador(filas: JsonElement?): List<FilaAgendador> =
    (filas as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
        val id = f.texto("agendada_por")?.takeIf { it.isNotBlank() }
        val asis = f.entero("asistieron"); val noAsis = f.entero("no_asistieron")
        FilaAgendador(
            id = id,
            nombre = if (id != null) f.texto("nombre")?.trim()?.takeIf { it.isNotEmpty() } ?: "Ya no está en el equipo" else SIN_PERSONA,
            rol = if (id != null) f.texto("rol")?.trim().orEmpty() else "",
            agendadas = f.entero("agendadas"), asistieron = asis, noAsistieron = noAsis,
            canceladas = f.entero("canceladas"), porVenir = f.entero("por_venir"), pacientesNuevos = f.entero("pacientes_nuevos"),
            asistencia = porcentajeAsistencia(asis, noAsis),
        )
    }.sortedWith(compareBy<FilaAgendador> { it.id == null }.thenByDescending { it.agendadas }.thenBy { it.nombre })

fun totalesAgendador(filas: List<FilaAgendador>): FilaAgendador {
    val asis = filas.sumOf { it.asistieron }; val noAsis = filas.sumOf { it.noAsistieron }
    return FilaAgendador(
        id = "total", nombre = "Total", rol = "",
        agendadas = filas.sumOf { it.agendadas }, asistieron = asis, noAsistieron = noAsis,
        canceladas = filas.sumOf { it.canceladas }, porVenir = filas.sumOf { it.porVenir },
        pacientesNuevos = filas.sumOf { it.pacientesNuevos }, asistencia = porcentajeAsistencia(asis, noAsis),
    )
}

// ── Cierres de caja ─────────────────────────────────────────────────────────

enum class ResultadoArqueo(val etiqueta: String) { CUADRO("Cuadró"), SOBRA("Sobró"), FALTA("Faltó") }

/** Menos de un céntimo de diferencia es un cierre que cuadró. */
fun resultadoArqueo(diferencia: Double): ResultadoArqueo = when {
    abs(diferencia) < 0.005 -> ResultadoArqueo.CUADRO
    diferencia > 0 -> ResultadoArqueo.SOBRA
    else -> ResultadoArqueo.FALTA
}

data class FilaCierre(
    val id: String,
    val fecha: String,
    val sedeId: String?,
    val ingresos: Double,
    val egresos: Double,
    val esperado: Double,
    val contado: Double,
    /** contado − esperado: positivo = sobró, negativo = faltó. */
    val diferencia: Double,
    val resultado: ResultadoArqueo,
    /** Lo retirado del cajón al cerrar; null = no se declaró (cierres antiguos). */
    val retirado: Double?,
    val cerradoPor: String?,
    val nota: String,
)

private fun r2(x: Double) = (x * 100).roundToInt() / 100.0

/** Las filas del reporte, de la más reciente a la más antigua. */
fun filasDeCierres(cierres: List<JsonObject>): List<FilaCierre> = cierres.map { c ->
    val esperado = c.decimal("efectivo_esperado") ?: 0.0
    val contado = c.decimal("efectivo_contado") ?: 0.0
    // La diferencia guardada manda; si falta, se calcula.
    val dif = c.decimal("diferencia") ?: r2(contado - esperado)
    FilaCierre(
        id = c.texto("id").orEmpty(), fecha = c.texto("fecha").orEmpty().take(10), sedeId = c.texto("sede_id"),
        ingresos = c.decimal("total_ingresos") ?: 0.0, egresos = c.decimal("total_egresos") ?: 0.0,
        esperado = esperado, contado = contado, diferencia = dif, resultado = resultadoArqueo(dif),
        retirado = c.decimal("retirado"), cerradoPor = c.texto("cerrado_por"), nota = c.texto("nota")?.trim().orEmpty(),
    ) to c.texto("cerrado_at").orEmpty()
}.sortedWith(compareByDescending<Pair<FilaCierre, String>> { it.first.fecha }.thenByDescending { it.second }).map { it.first }

data class ResumenCierres(
    val cierres: Int, val cuadraron: Int, val conDiferencia: Int,
    val ingresos: Double, val egresos: Double, val sobrante: Double, val faltante: Double, val retirado: Double,
)

fun resumenCierres(filas: List<FilaCierre>): ResumenCierres = ResumenCierres(
    cierres = filas.size,
    cuadraron = filas.count { it.resultado == ResultadoArqueo.CUADRO },
    conDiferencia = filas.count { it.resultado != ResultadoArqueo.CUADRO },
    ingresos = r2(filas.sumOf { it.ingresos }), egresos = r2(filas.sumOf { it.egresos }),
    sobrante = r2(filas.filter { it.diferencia > 0 }.sumOf { it.diferencia }),
    faltante = r2(filas.filter { it.diferencia < 0 }.sumOf { -it.diferencia }),
    retirado = r2(filas.sumOf { it.retirado ?: 0.0 }),
)

// ── Lectura ─────────────────────────────────────────────────────────────────

object ReportesEquipoRepo {
    /** null = no se pudo leer. */
    suspend fun quienAgendo(p: Periodo): List<FilaAgendador>? = try {
        filasAgendador(
            Supabase.client.postgrest.rpc("citas_por_agendador", buildJsonObject { put("p_desde", p.desde); put("p_hasta", p.hasta) })
                .decodeAs<JsonElement>(),
        )
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /** Cierres del periodo (de la sede, si hay una elegida) y el nombre de quien cerró. null = error. */
    suspend fun cierres(p: Periodo, sedeId: String?): Pair<List<FilaCierre>, Map<String, String>>? = try {
        val filas = Supabase.client.postgrest["cierres_caja"].select(
            Columns.list("id, fecha, sede_id, total_ingresos, total_egresos, efectivo_esperado, efectivo_contado, diferencia, retirado, nota, cerrado_por, cerrado_at"),
        ) {
            filter {
                gte("fecha", p.desde); lte("fecha", p.hasta)
                if (sedeId != null) eq("sede_id", sedeId)
            }
            order("fecha", Order.DESCENDING)
            limit(1000)
        }.decodeList<JsonObject>()
        val cierres = filasDeCierres(filas)
        val ids = cierres.mapNotNull { it.cerradoPor }.distinct()
        val nombres = if (ids.isEmpty()) emptyMap() else runCatching {
            Supabase.client.postgrest["perfiles"].select(Columns.list("id, nombre")) { filter { isIn("id", ids) } }
                .decodeList<JsonObject>().associate { it.texto("id").orEmpty() to it.texto("nombre").orEmpty() }
        }.getOrDefault(emptyMap())
        cierres to nombres
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}


// ── 🧾 Servicios y tratamientos (25/11/2026) ─────────────────────────────────
// Gemelo de lib/servicios-reporte.ts (web). Sumas en la base: citas_por_servicio,
// citas_por_servicio_detalle y tratamientos_vendidos (migración 20261227100000).

data class FilaServicio(
    val servicioId: String?, val nombre: String, val tipo: String?,
    val citas: Int, val atendidas: Int, val noAsistieron: Int, val canceladas: Int, val porVenir: Int, val pacientes: Int,
)

fun filasServicios(filas: JsonElement?): List<FilaServicio> =
    (filas as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
        val id = f.texto("servicio_id")?.takeIf { it.isNotBlank() }
        val tipo = if (id == null) f.texto("tipo")?.trim()?.ifEmpty { null } ?: "Sin tipo" else null
        FilaServicio(
            servicioId = id,
            nombre = if (id != null) f.texto("servicio")?.trim()?.ifEmpty { null } ?: "Servicio eliminado" else "$tipo (sin servicio)",
            tipo = tipo,
            citas = f.entero("citas"), atendidas = f.entero("atendidas"), noAsistieron = f.entero("no_asistieron"),
            canceladas = f.entero("canceladas"), porVenir = f.entero("por_venir"), pacientes = f.entero("pacientes"),
        )
    }.sortedWith(compareBy<FilaServicio> { it.servicioId == null }.thenByDescending { it.citas }.thenBy { it.nombre })

data class CitaServicio(val id: String, val fecha: String, val hora: String, val estado: String, val noAsistio: Boolean, val pacienteId: String?, val paciente: String, val profesional: String)

fun citasDeServicio(filas: JsonElement?): List<CitaServicio> =
    (filas as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
        CitaServicio(
            id = f.texto("id").orEmpty(), fecha = f.texto("fecha").orEmpty().take(10), hora = f.texto("hora").orEmpty().take(5),
            estado = f.texto("estado").orEmpty(), noAsistio = f.texto("no_asistio") == "true", pacienteId = f.texto("paciente_id"),
            paciente = f.texto("paciente")?.trim()?.ifEmpty { null } ?: "Paciente",
            profesional = f.texto("profesional")?.trim()?.ifEmpty { null } ?: "Sin profesional",
        )
    }

/** Atendida / Cancelada / No asistió (una pasada sin atender) / Por venir. */
fun estadoVisible(estado: String, noAsistio: Boolean, fecha: String, hoy: String): String = when {
    estado == "Completada" -> "Atendida"
    estado == "Cancelada" -> "Cancelada"
    noAsistio || fecha < hoy -> "No asistió"
    else -> "Por venir"
}

data class TratamientoVendido(
    val id: String, val fecha: String, val pacienteId: String?, val paciente: String, val servicioId: String?, val servicio: String,
    val profesional: String, val modalidad: String, val estado: String, val totalSesiones: Int?, val cantidadUnidades: Int?,
    val valor: Double, val pagado: Double, val saldo: Double,
)

fun tratamientosVendidos(filas: JsonElement?): List<TratamientoVendido> =
    (filas as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map { f ->
        val valor = f.decimal("valor") ?: 0.0
        val pagado = f.decimal("pagado") ?: 0.0
        TratamientoVendido(
            id = f.texto("id").orEmpty(), fecha = f.texto("fecha").orEmpty().take(10), pacienteId = f.texto("paciente_id"),
            paciente = f.texto("paciente")?.trim()?.ifEmpty { null } ?: "Paciente",
            servicioId = f.texto("servicio_id"), servicio = f.texto("servicio")?.trim()?.ifEmpty { null } ?: "Sin servicio",
            profesional = f.texto("profesional")?.trim()?.ifEmpty { null } ?: "Sin profesional",
            modalidad = f.texto("modalidad").orEmpty(), estado = f.texto("estado").orEmpty(),
            totalSesiones = f.texto("total_sesiones")?.toDoubleOrNull()?.toInt(),
            cantidadUnidades = f.texto("cantidad_unidades")?.toDoubleOrNull()?.toInt(),
            valor = valor, pagado = pagado, saldo = r2(maxOf(0.0, valor - pagado)),
        )
    }

fun textoModalidad(t: TratamientoVendido): String = when (t.modalidad) {
    "Paquete" -> t.totalSesiones?.let { "Paquete · $it sesiones" } ?: "Paquete"
    "Unidades" -> t.cantidadUnidades?.let { "Unidades · $it" } ?: "Unidades"
    "Sesión suelta" -> "Sesiones sueltas"
    "Consulta" -> "Pago único"
    else -> t.modalidad.ifBlank { "—" }
}

data class ResumenVendido(val servicio: String, val cantidad: Int, val valor: Double, val pagado: Double, val saldo: Double)

/** Totales por servicio de lo vendido (más vendido primero). */
fun resumenVendidos(lista: List<TratamientoVendido>): List<ResumenVendido> =
    lista.groupBy { it.servicioId ?: "—${it.servicio}" }.values.map { g ->
        ResumenVendido(g.first().servicio, g.size, r2(g.sumOf { it.valor }), r2(g.sumOf { it.pagado }), r2(g.sumOf { it.saldo }))
    }.sortedWith(compareByDescending<ResumenVendido> { it.cantidad }.thenByDescending { it.valor }.thenBy { it.servicio })

object ServiciosReporteRepo {
    suspend fun citasPorServicio(p: Periodo, porAgendada: Boolean, soloNuevos: Boolean): List<FilaServicio>? = try {
        filasServicios(Supabase.client.postgrest.rpc("citas_por_servicio", buildJsonObject {
            put("p_desde", p.desde); put("p_hasta", p.hasta); put("p_por", if (porAgendada) "agendada" else "cita"); put("p_solo_nuevos", soloNuevos)
        }).decodeAs<JsonElement>())
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    suspend fun detalle(p: Periodo, porAgendada: Boolean, soloNuevos: Boolean, f: FilaServicio): List<CitaServicio>? = try {
        citasDeServicio(Supabase.client.postgrest.rpc("citas_por_servicio_detalle", buildJsonObject {
            put("p_desde", p.desde); put("p_hasta", p.hasta); put("p_por", if (porAgendada) "agendada" else "cita"); put("p_solo_nuevos", soloNuevos)
            f.servicioId?.let { put("p_servicio", it) }
            f.tipo?.let { put("p_tipo", it) }
        }).decodeAs<JsonElement>())
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    suspend fun vendidos(p: Periodo): List<TratamientoVendido>? = try {
        tratamientosVendidos(Supabase.client.postgrest.rpc("tratamientos_vendidos", buildJsonObject {
            put("p_desde", p.desde); put("p_hasta", p.hasta)
        }).decodeAs<JsonElement>())
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
}
