package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.enviarOEncolarDetalle

// ─────────────────────────────────────────────────────────────────────────────
// Retención (/seguimiento en la web). Lecturas: las MISMAS funciones de la base
// que usa la web (retencion_resumen / retencion_llamar, SECURITY INVOKER: la RLS
// y el alcance por rol los aplica la base). Escrituras: los endpoints que ya usa
// la web (/api/staff/tratamiento/accion "no_volvio", /api/staff/paciente/descartar).
// ─────────────────────────────────────────────────────────────────────────────

/** Un registrado que nunca llegó a tratamiento ("Nunca empezaron"). */
data class NuncaEmpezo(
    val id: String, val nombre: String, val telefono: String?, val diagnostico: String?,
    val fechaIngreso: String?, val diasDesdeIngreso: Int, val hizoCita: Boolean,
    val descartado: Boolean, val descartadoMotivo: String?,
)

/** Calificación promedio de una persona del equipo (no es ranking: orden alfabético). */
data class SatisfaccionPersona(val nombre: String, val promedio: Double, val total: Int, val quejaTop: Pair<String, Int>?)

data class PresupuestoPendiente(
    val pacienteId: String, val nombre: String, val telefono: String?, val desde: String,
    val resumen: String, val suma: Double, val enlaceEnviado: String?,
)

data class PresupuestoAceptado(val pacienteId: String, val nombre: String, val aceptadoAt: String, val total: Double?)

data class CuotaPorCobrar(
    val cuota: CuotaConEstado, val nCuotas: Int, val pacienteId: String, val nombre: String,
    val telefono: String?, val servicio: String,
)

object RetencionRepo {

    private val http = crearHttpClient()
    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken
    private val json = Json { ignoreUnknownKeys = true }

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }

    // ── Lista y resumen (funciones de la base) ──────────────────────────────

    suspend fun resumen(hoy: String): ResumenRetencion {
        val r = Supabase.client.postgrest.rpc("retencion_resumen", buildJsonObject { put("p_hoy", hoy) })
        return resumenRetencionDesde(json.parseToJsonElement(r.data).jsonObject)
    }

    /** Una página de la lista filtrada + el total (un viaje). */
    suspend fun llamar(
        hoy: String, filtros: JsonObject, orden: OrdenRet, asc: Boolean, limite: Int, offset: Int,
    ): Pair<Int, List<FilaRetencion>> {
        val r = Supabase.client.postgrest.rpc("retencion_llamar", buildJsonObject {
            put("p_hoy", hoy); put("p_filtros", filtros); put("p_orden", orden.clave)
            put("p_asc", asc); put("p_limite", limite); put("p_offset", offset)
        })
        val o = json.parseToJsonElement(r.data).jsonObject
        val total = o.s("total")?.toDoubleOrNull()?.toInt() ?: 0
        val filas = (o["filas"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::filaRetencionDesde) }
        return total to filas
    }

    /** Números de las pestañas dentales (null si la función no existe / falla). */
    suspend fun conteosDental(hoy: String): Pair<Int, Int>? = try {
        val r = Supabase.client.postgrest.rpc("conteos_retencion_dental", buildJsonObject { put("p_hoy", hoy) })
        val o = json.parseToJsonElement(r.data).jsonObject
        (o.s("presupuestos")?.toDoubleOrNull()?.toInt() ?: 0) to (o.s("cuotas")?.toDoubleOrNull()?.toInt() ?: 0)
    } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) { null }

    // ── Nunca empezaron ─────────────────────────────────────────────────────

    private const val TOPE_PACIENTES = 2000

    private suspend fun idsConAlgo(tabla: String, ids: List<String>): Set<String> = coroutineScope {
        ids.chunked(100).map { lote ->
            async {
                Supabase.client.postgrest[tabla].select(Columns.list("paciente_id")) {
                    filter { isIn("paciente_id", lote) }
                }.decodeList<JsonObject>().mapNotNull { it.s("paciente_id") }
            }
        }.awaitAll().flatten().toSet()
    }

    /** Registrados que nunca llegaron a tratamiento, los más antiguos primero. */
    suspend fun nuncaEmpezaron(hoy: String): List<NuncaEmpezo> {
        val pacs = Supabase.client.postgrest["pacientes"]
            .select(Columns.list("id, nombre, telefono, diagnostico, fecha_ingreso, estado, descartado_at, descartado_motivo")) {
                filter { neq("estado", "Inactivo") }
                order("fecha_ingreso", Order.ASCENDING)
                limit(TOPE_PACIENTES.toLong())
            }.decodeList<JsonObject>()
        val ids = pacs.mapNotNull { it.s("id") }
        val (conCitas, conTrat) = coroutineScope {
            val a = async { idsConAlgo("citas", ids) }
            val b = async { idsConAlgo("tratamientos", ids) }
            a.await() to b.await()
        }
        val h = LocalDate.parse(hoy.take(10))
        return pacs.mapNotNull { p ->
            val id = p.s("id") ?: return@mapNotNull null
            if (id in conTrat) return@mapNotNull null
            val ingreso = p.s("fecha_ingreso")
            val dias = runCatching { LocalDate.parse(ingreso!!.take(10)).daysUntil(h) }.getOrDefault(0)
            NuncaEmpezo(
                id = id, nombre = p.s("nombre").orEmpty(), telefono = p.s("telefono"), diagnostico = p.s("diagnostico"),
                fechaIngreso = ingreso, diasDesdeIngreso = dias, hizoCita = id in conCitas,
                descartado = p.s("descartado_at") != null, descartadoMotivo = p.s("descartado_motivo"),
            )
        }
    }

    /** Descartar (o recuperar) un registrado de la lista. Permiso `pacientes`: lo valida el servidor. */
    suspend fun descartar(pacienteId: String, descartar: Boolean, motivo: String): ResultadoEscritura =
        enviarOEncolarDetalle("paciente:descartar", "/api/staff/paciente/descartar", buildJsonObject {
            put("pacienteId", pacienteId); put("descartar", descartar); put("motivo", motivo)
        })

    // ── Cómo califican al equipo ────────────────────────────────────────────

    suspend fun satisfaccion(): List<SatisfaccionPersona> {
        // Solo las de la encuesta al alta (las viejas de cita no tienen a quién atribuirlas).
        val filas = Supabase.client.postgrest["resenas"]
            .select(Columns.raw("calificacion, aspectos, calificacion_recepcion, aspectos_recepcion, terapeuta:terapeutas(nombre)")) {
                filter { eq("origen", "alta") }
                order("created_at", Order.DESCENDING)
                limit(500)
            }.decodeList<JsonObject>()
        class Acum { var suma = 0.0; var total = 0; val quejas = mutableMapOf<String, Int>() }
        val porNombre = linkedMapOf<String, Acum>()
        val rec = Acum()
        fun aspectos(a: Any?): List<String> = (a as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        for (r in filas) {
            val nombre = (r["terapeuta"] as? JsonObject)?.s("nombre")
            val cal = r.s("calificacion")?.toDoubleOrNull()
            if (nombre != null && cal != null) {
                val e = porNombre.getOrPut(nombre) { Acum() }
                e.suma += cal; e.total++
                aspectos(r["aspectos"]).forEach { e.quejas[it] = (e.quejas[it] ?: 0) + 1 }
            }
            r.s("calificacion_recepcion")?.toDoubleOrNull()?.let { cr ->
                rec.suma += cr; rec.total++
                aspectos(r["aspectos_recepcion"]).forEach { rec.quejas[it] = (rec.quejas[it] ?: 0) + 1 }
            }
        }
        fun fila(n: String, a: Acum) = SatisfaccionPersona(
            n, kotlin.math.round(a.suma / a.total * 10) / 10.0, a.total, a.quejas.maxByOrNull { it.value }?.let { it.key to it.value },
        )
        val personas = porNombre.entries.sortedBy { it.key.lowercase() }.map { fila(it.key, it.value) }
        // Recepción al final, separada: el sistema no guarda quién estaba en el mostrador.
        return if (rec.total > 0) personas + fila("Recepción", rec) else personas
    }

    // ── Redactar con IA (mismo endpoint que la web; solo plan Plus) ─────────

    /** Devuelve el texto o lanza con el motivo. */
    suspend fun redactarMensaje(objetivo: String, nombrePaciente: String, detalle: String): String {
        val tk = token() ?: error("Tu sesión expiró. Vuelve a entrar.")
        val resp = http.post("${Supabase.SITE_URL}/api/ai/redactar-mensaje") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("objetivo", objetivo); put("canal", "whatsapp"); put("nombrePaciente", nombrePaciente); put("detalle", detalle)
            }.toString())
        }
        val texto = resp.bodyAsText().trim()
        if (!resp.status.isSuccess() || texto.isEmpty()) error(texto.ifEmpty { "No se pudo generar el mensaje." })
        return texto
    }

    // ── Dental: presupuestos sin aceptar ────────────────────────────────────

    /** (pendientes, aceptados por el enlace pero sin tratamiento aún). Mismo cálculo que la web. */
    suspend fun presupuestos(): Pair<List<PresupuestoPendiente>, List<PresupuestoAceptado>> = coroutineScope {
        val hallD = async {
            Supabase.client.postgrest["dientes_hallazgos"]
                .select(Columns.raw("id, paciente_id, diente, diente_hasta, hallazgo_id, superficies, estado, tratamiento_id, fecha, paciente:pacientes(nombre, telefono, estado)")) {
                    filter { eq("estado", "Pendiente"); exact("tratamiento_id", null) }
                    order("fecha", Order.DESCENDING)
                    limit(2000)
                }.decodeList<JsonObject>()
        }
        val catD = async { OdontogramaRepo.catalogo() }
        val procD = async { OdontogramaRepo.procedimientos() }
        val enlD = async {
            try {
                Supabase.client.postgrest["presupuestos_dentales"]
                    .select(Columns.list("paciente_id, estado, vence_at, created_at, aceptado_at, total_aceptado")) {
                        order("created_at", Order.DESCENDING); limit(2000)
                    }.decodeList<JsonObject>()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (_: Exception) { emptyList() }
        }
        val hall = hallD.await(); val cat = catD.await(); val procs = procD.await(); val enl = enlD.await()

        val ultimoEnlace = linkedMapOf<String, JsonObject>()
        for (e in enl) e.s("paciente_id")?.let { if (it !in ultimoEnlace) ultimoEnlace[it] = e }
        val porId = cat.associateBy { it.id }
        val porPaciente = linkedMapOf<String, MutableList<JsonObject>>()
        for (r in hall) {
            val pac = r["paciente"] as? JsonObject ?: continue
            if (pac.s("estado") == "Inactivo") continue
            val h = porId[r.s("hallazgo_id")] ?: continue
            // Lo que no es "por hacer" (ausente, trabajo previo en azul) no es un presupuesto.
            if (h.marcaAusente || h.color.equals(COLOR_REALIZADO, ignoreCase = true)) continue
            porPaciente.getOrPut(r.s("paciente_id") ?: continue) { mutableListOf() }.add(r)
        }
        val pendientes = mutableListOf<PresupuestoPendiente>()
        val aceptados = mutableListOf<PresupuestoAceptado>()
        for ((pid, rs) in porPaciente) {
            val dientes = rs.map {
                DienteHallazgo(
                    id = it.s("id").orEmpty(), pacienteId = pid, diente = it.s("diente").orEmpty(),
                    hallazgoId = it.s("hallazgo_id").orEmpty(), estado = "Pendiente",
                    fecha = it.s("fecha").orEmpty(), dienteHasta = it.s("diente_hasta"),
                )
            }
            val (lineas, _) = agruparPresupuesto(dientes, cat, procs)
            val suma = lineas.sumOf { it.subtotal }
            val conteo = linkedMapOf<String, Int>()
            for (r in rs) { val n = porId[r.s("hallazgo_id")]?.nombre ?: "—"; conteo[n] = (conteo[n] ?: 0) + 1 }
            val resumen = conteo.entries.joinToString(", ") { (n, k) -> if (k > 1) "$n ×$k" else n }
            val desde = rs.maxOf { it.s("fecha").orEmpty() }
            val pac = rs.first()["paciente"] as JsonObject
            val enlace = ultimoEnlace[pid]
            val estado = enlace?.let { estadoVisibleEnlace(it.s("estado").orEmpty(), it.s("vence_at")) }
            val aceptadoAt = enlace?.s("aceptado_at")
            // Aceptado desde el enlace DESPUÉS de lo último evaluado: ya no está "sin aceptar".
            if (enlace != null && estado == "Aceptado" && aceptadoAt != null && aceptadoAt.take(10) >= desde) {
                aceptados += PresupuestoAceptado(pid, pac.s("nombre").orEmpty(), aceptadoAt, enlace.s("total_aceptado")?.toDoubleOrNull())
                continue
            }
            pendientes += PresupuestoPendiente(
                pid, pac.s("nombre").orEmpty(), pac.s("telefono"), desde, resumen, suma,
                if (enlace != null && estado == "Enviado") enlace.s("created_at") else null,
            )
        }
        pendientes.sortedByDescending { it.desde } to aceptados.sortedByDescending { it.aceptadoAt }
    }

    private fun estadoVisibleEnlace(estado: String, venceAt: String?): String {
        if (estado == "Aceptado" || estado == "Rechazado" || estado == "Vencido") return estado
        val vence = runCatching { kotlinx.datetime.Instant.parse(venceAt!!) }.getOrNull()
        if (vence != null && vence < kotlinx.datetime.Clock.System.now()) return "Vencido"
        return "Enviado"
    }

    // ── Dental: cuotas por cobrar ───────────────────────────────────────────

    suspend fun cuotas(hoy: String): List<CuotaPorCobrar> {
        val filas = Supabase.client.postgrest["cuotas_tratamiento"]
            .select(Columns.raw("numero, monto, vence, tratamiento_id, paciente_id, paciente:pacientes(nombre, telefono), tratamiento:tratamientos(estado, procedimiento:procedimientos(nombre))")) {
                order("numero", Order.ASCENDING); limit(5000)
            }.decodeList<JsonObject>()
            .filter {
                val e = (it["tratamiento"] as? JsonObject)?.s("estado")
                (it["tratamiento"] as? JsonObject) != null && e != "Cancelado" && e != "Eliminado"
            }
        val ids = filas.mapNotNull { it.s("tratamiento_id") }.distinct()
        val pagado = mutableMapOf<String, Double>()
        // En tandas y en paralelo: un `in` con cientos de ids revienta el largo de la URL.
        val tandas = coroutineScope {
            ids.chunked(150).map { lote ->
                async {
                    Supabase.client.postgrest["pagos_tratamiento"].select(Columns.list("tratamiento_id, monto")) {
                        filter { isIn("tratamiento_id", lote) }
                    }.decodeList<JsonObject>()
                }
            }.awaitAll()
        }
        for (p in tandas.flatten()) {
            val t = p.s("tratamiento_id") ?: continue
            pagado[t] = (pagado[t] ?: 0.0) + (p.s("monto")?.toDoubleOrNull() ?: 0.0)
        }
        val out = mutableListOf<CuotaPorCobrar>()
        for ((tid, cs) in filas.groupBy { it.s("tratamiento_id") }) {
            if (tid == null) continue
            val plan = cs.mapNotNull { c ->
                CuotaPlan(c.s("numero")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null,
                    c.s("monto")?.toDoubleOrNull() ?: 0.0, c.s("vence") ?: return@mapNotNull null)
            }
            val nCuotas = plan.count { it.numero > 0 }
            val pac = cs.first()["paciente"] as? JsonObject
            val servicio = ((cs.first()["tratamiento"] as? JsonObject)?.get("procedimiento") as? JsonObject)?.s("nombre") ?: "Tratamiento"
            for (cuota in estadoCuotas(plan, pagado[tid] ?: 0.0, hoy)) {
                if (!cuotaParaAvisar(cuota)) continue
                out += CuotaPorCobrar(cuota, nCuotas, cs.first().s("paciente_id").orEmpty(),
                    pac?.s("nombre") ?: "—", pac?.s("telefono"), servicio)
            }
        }
        return out.sortedBy { it.cuota.vence }
    }
}
