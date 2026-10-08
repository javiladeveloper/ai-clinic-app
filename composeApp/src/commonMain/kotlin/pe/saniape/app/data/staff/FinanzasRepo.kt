package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient

/** Cierre de caja de un día (respuesta de GET /api/staff/caja/cierre). */
data class DiaCierre(
    val fecha: String,
    val multiSede: Boolean,
    val cerrable: Boolean,
    val cerradasConsolidado: Int?,
    val totalIngresos: Double,
    val totalEgresos: Double,
    val neto: Double,
    val esperado: Double,
    val porMetodo: List<Pair<String, Double>>,
    val cierre: CierreCaja?,
    val movimientos: List<MovimientoCierre>,
)

data class CierreCaja(val id: String, val efectivoContado: Double, val diferencia: Double, val nota: String?, val cerradoAt: String?)
data class MovimientoCierre(val tipo: String, val monto: Double, val metodoPago: String?, val descripcion: String, val createdAt: String?)

/** Un gasto fijo con su estado en la caja (GET /api/staff/gastos-recurrentes). */
data class GastoFijo(
    val id: String,
    val nombre: String,
    val categoria: String,
    val monto: Double,
    val frecuencia: String,
    val diaCobro: Int?,
    val fechaUnica: String?,
    val activo: Boolean,
    val pagado: Boolean,
    val yaEnCaja: Boolean,
    val unicoCerrado: Boolean,
    /** Próxima fecha en que entra a la caja (YYYY-MM-DD) o null. */
    val proximo: String?,
)

data class GastosYCategorias(
    val gastos: List<GastoFijo>,
    val catEgreso: List<String>,
    val catIngreso: List<String>,
    val catGastoFijo: List<String>,
)

/** Consulta/evaluación atendida y sin cobrar (bloque "Por cobrar"). */
data class PendienteCobro(
    val citaId: String,
    val fecha: String,
    val hora: String?,
    val tipo: String,
    val costo: Double,
    val paciente: String,
    val pacienteId: String?,
    val profesional: String?,
)

/** Resultado de una escritura: null = ok; si no, el mensaje del servidor. */
data class RespuestaFinanzas(val ok: Boolean, val error: String? = null, val cuerpo: JsonObject? = null)

/**
 * "Finanzas y caja" nativo. Lecturas simples directo a Supabase (RLS por clínica;
 * multisede con [SedeActiva]); TODO lo que escribe va por /api/staff/… con la
 * misma lógica que la web (lib/…-acciones.ts): registrar/corregir movimientos,
 * cierre de caja y gastos fijos. La app no recalcula dinero.
 */
object FinanzasRepo {

    private val json = Json { ignoreUnknownKeys = true }
    private val http = crearHttpClient()

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken
    private fun sedeActual(): String? = SedeActiva.filtro?.sedeId

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
    private fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull

    // ── HTTP ────────────────────────────────────────────────────────────────

    private suspend fun leerRespuesta(resp: HttpResponse): RespuestaFinanzas {
        val texto = runCatching { resp.bodyAsText() }.getOrNull()
        val obj = texto?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (resp.status.value in 200..299) return RespuestaFinanzas(true, cuerpo = obj)
        val msg = obj?.str("error")?.takeIf { it.isNotBlank() } ?: when (resp.status.value) {
            401 -> "Tu sesión expiró. Vuelve a entrar."
            402 -> "Finanzas está disponible desde el plan Premium."
            403 -> "No tienes permiso para esto."
            else -> "No se pudo completar (HTTP ${resp.status.value})."
        }
        return RespuestaFinanzas(false, msg, obj)
    }

    private suspend fun llamar(bloque: suspend (String) -> HttpResponse): RespuestaFinanzas {
        val tk = token() ?: return RespuestaFinanzas(false, "Tu sesión expiró. Vuelve a entrar.")
        return try {
            leerRespuesta(bloque(tk))
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            RespuestaFinanzas(false, "Sin conexión. Revisa tu internet.")
        }
    }

    private suspend fun post(ruta: String, cuerpo: JsonObject) = llamar { tk ->
        http.post("${Supabase.SITE_URL}$ruta") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        }
    }

    private suspend fun patch(ruta: String, cuerpo: JsonObject) = llamar { tk ->
        http.patch("${Supabase.SITE_URL}$ruta") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        }
    }

    // ── Kardex ──────────────────────────────────────────────────────────────

    /**
     * Movimientos del rango (más recientes primero), con el paciente y quién lo
     * registró. Devuelve también si se alcanzó el tope (un total incompleto no
     * debe leerse como total).
     */
    suspend fun kardex(r: RangoFechas): Pair<List<MovimientoKardex>, Boolean> {
        val filas = Supabase.client.postgrest["movimientos"]
            .select(Columns.raw("id, tipo, categoria, descripcion, monto, fecha, metodo_pago, comprobante, comision_id, sesion_id, sede_id, created_at, paciente:pacientes(id, nombre)")) {
                filter {
                    r.desde?.let { gte("fecha", it) }
                    lte("fecha", r.hasta)
                    // Multisede: la sede activa (la principal incluye los sin sede).
                    filtroSede(SedeActiva.filtro)
                }
                order("fecha", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                limit(Finanzas.TOPE_MOVIMIENTOS.toLong())
            }
            .decodeList<JsonObject>()
        val movs = filas.mapNotNull { o ->
            val pac = o["paciente"] as? JsonObject
            MovimientoKardex(
                id = o.str("id") ?: return@mapNotNull null,
                tipo = o.str("tipo") ?: "Ingreso",
                categoria = o.str("categoria") ?: "",
                descripcion = o.str("descripcion"),
                monto = o.dbl("monto") ?: 0.0,
                fecha = o.str("fecha") ?: "",
                metodoPago = o.str("metodo_pago"),
                comprobante = o.str("comprobante"),
                comisionId = o.str("comision_id"),
                sesionId = o.str("sesion_id"),
                sedeId = o.str("sede_id"),
                pacienteId = pac?.str("id"),
                pacienteNombre = pac?.str("nombre"),
            )
        }
        val conAutor = runCatching { conAutores(movs) }.getOrDefault(movs)
        return conAutor to (filas.size >= Finanzas.TOPE_MOVIMIENTOS)
    }

    /** `cita:<id>` o `cita:<id>#2` (partes de un cobro dividido) → id de la cita. */
    internal fun citaIdDeComprobante(c: String?): String? =
        Regex("^cita:([^#\\s]+)(?:#\\d+)?$").find(c ?: "")?.groupValues?.get(1)

    /**
     * Quién registró cada movimiento: no vive en `movimientos`, pero el
     * comprobante enlaza al pago (creado_por) o a la cita (pagada_por). Pocas
     * consultas acotadas a lo visible, no una por fila (como la web).
     */
    private suspend fun conAutores(movs: List<MovimientoKardex>): List<MovimientoKardex> {
        val pagoIds = movs.mapNotNull { m -> m.comprobante?.takeIf { it.startsWith("pago:") }?.removePrefix("pago:") }.distinct()
        val citaIds = movs.mapNotNull { citaIdDeComprobante(it.comprobante) }.distinct()
        if (pagoIds.isEmpty() && citaIds.isEmpty()) return movs
        val autorPago = HashMap<String, String>()
        val autorCita = HashMap<String, String>()
        pagoIds.chunked(150).forEach { lote ->
            Supabase.client.postgrest["pagos_tratamiento"].select(Columns.raw("id, creado_por")) { filter { isIn("id", lote) } }
                .decodeList<JsonObject>().forEach { o -> val a = o.str("creado_por"); val i = o.str("id"); if (a != null && i != null) autorPago[i] = a }
        }
        citaIds.chunked(150).forEach { lote ->
            Supabase.client.postgrest["citas"].select(Columns.raw("id, pagada_por")) { filter { isIn("id", lote) } }
                .decodeList<JsonObject>().forEach { o -> val a = o.str("pagada_por"); val i = o.str("id"); if (a != null && i != null) autorCita[i] = a }
        }
        val ids = (autorPago.values + autorCita.values).distinct()
        if (ids.isEmpty()) return movs
        val nombre = HashMap<String, String>()
        Supabase.client.postgrest["perfiles"].select(Columns.raw("id, nombre")) { filter { isIn("id", ids) } }
            .decodeList<JsonObject>().forEach { o -> val n = o.str("nombre"); val i = o.str("id"); if (n != null && i != null) nombre[i] = n }
        return movs.map { m ->
            val c = m.comprobante ?: return@map m
            val autorId = if (c.startsWith("pago:")) autorPago[c.removePrefix("pago:")] else citaIdDeComprobante(c)?.let { autorCita[it] }
            autorId?.let { nombre[it] }?.let { m.copy(autor = it) } ?: m
        }
    }

    /** Las categorías que la clínica ya usó (para sugerir en el formulario). */
    fun categoriasUsadas(movs: List<MovimientoKardex>, tipo: String): List<String> =
        movs.filter { it.tipo == tipo && it.categoria.isNotBlank() && it.categoria !in listOf("Pago paciente", "Comisión terapeuta") }
            .map { it.categoria }.distinct()

    /** POST /api/staff/movimiento/registrar (misma validación e insert que la web). */
    suspend fun registrar(
        tipo: String, categoria: String, descripcion: String, monto: Double, metodo: String?, comprobante: String?,
    ): RespuestaFinanzas = post("/api/staff/movimiento/registrar", buildJsonObject {
        put("tipo", tipo); put("categoria", categoria); put("descripcion", descripcion); put("monto", monto)
        if (!metodo.isNullOrBlank()) put("metodo", metodo)
        comprobante?.trim()?.takeIf { it.isNotEmpty() }?.let { put("comprobante", it) }
        sedeActual()?.let { put("sede", it) }
    })

    /** Corregir un movimiento MANUAL (solo Admin; nunca un día con caja cerrada — lo decide el servidor). */
    suspend fun editar(
        id: String, tipo: String, categoria: String, descripcion: String, monto: Double, fecha: String, comprobante: String?,
    ): RespuestaFinanzas = post("/api/staff/movimiento/editar", buildJsonObject {
        put("movimientoId", id); put("tipo", tipo); put("categoria", categoria); put("descripcion", descripcion)
        put("monto", monto); put("fecha", fecha)
        comprobante?.trim()?.takeIf { it.isNotEmpty() }?.let { put("comprobante", it) }
    })

    suspend fun borrar(id: String): RespuestaFinanzas =
        post("/api/staff/movimiento/borrar", buildJsonObject { put("movimientoId", id) })

    /** Corregir SOLO con qué se pagó (también en los del sistema). */
    suspend fun cambiarMetodo(id: String, metodo: String): RespuestaFinanzas =
        post("/api/staff/movimiento/metodo", buildJsonObject { put("movimientoId", id); put("metodo", metodo) })

    // ── Cierre de caja ──────────────────────────────────────────────────────

    suspend fun cierre(fecha: String): Pair<DiaCierre?, String?> {
        val r = llamar { tk ->
            http.get("${Supabase.SITE_URL}/api/staff/caja/cierre") {
                header("Authorization", "Bearer $tk")
                parameter("fecha", fecha)
                sedeActual()?.let { parameter("sede", it) }
            }
        }
        if (!r.ok) return null to r.error
        return (r.cuerpo?.let { parsearDiaCierre(it) } ?: return null to "Respuesta inesperada del servidor.") to null
    }

    /** Cierra la caja del día (o actualiza el cierre si ya estaba cerrada). */
    suspend fun cerrar(fecha: String, contado: Double, nota: String?, actualizar: Boolean): RespuestaFinanzas {
        val cuerpo = buildJsonObject {
            put("fecha", fecha); put("efectivoContado", contado)
            nota?.trim()?.takeIf { it.isNotEmpty() }?.let { put("nota", it) }
            sedeActual()?.let { put("sede", it) }
        }
        return if (actualizar) patch("/api/staff/caja/cierre", cuerpo) else post("/api/staff/caja/cierre", cuerpo)
    }

    internal fun parsearDiaCierre(o: JsonObject): DiaCierre? {
        val fecha = o.str("fecha") ?: return null
        val c = o["cierre"] as? JsonObject
        return DiaCierre(
            fecha = fecha,
            multiSede = o.bool("multiSede") ?: false,
            cerrable = o.bool("cerrable") ?: true,
            cerradasConsolidado = o.int("cerradasConsolidado"),
            totalIngresos = o.dbl("totalIngresos") ?: 0.0,
            totalEgresos = o.dbl("totalEgresos") ?: 0.0,
            neto = o.dbl("neto") ?: 0.0,
            esperado = o.dbl("esperado") ?: 0.0,
            porMetodo = (o["porMetodo"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                (x.str("metodo") ?: return@mapNotNull null) to (x.dbl("total") ?: 0.0)
            },
            cierre = c?.let {
                CierreCaja(
                    id = it.str("id") ?: "",
                    efectivoContado = it.dbl("efectivoContado") ?: 0.0,
                    diferencia = it.dbl("diferencia") ?: 0.0,
                    nota = it.str("nota"),
                    cerradoAt = it.str("cerradoAt"),
                )
            },
            movimientos = (o["movimientos"] as? JsonArray).orEmpty().mapNotNull { e ->
                val x = e as? JsonObject ?: return@mapNotNull null
                MovimientoCierre(
                    tipo = x.str("tipo") ?: "Ingreso",
                    monto = x.dbl("monto") ?: 0.0,
                    metodoPago = x.str("metodoPago"),
                    descripcion = x.str("descripcion") ?: x.str("categoria") ?: "",
                    createdAt = x.str("createdAt"),
                )
            },
        )
    }

    // ── Gastos fijos ────────────────────────────────────────────────────────

    suspend fun gastos(): Pair<GastosYCategorias?, String?> {
        val r = llamar { tk ->
            http.get("${Supabase.SITE_URL}/api/staff/gastos-recurrentes") {
                header("Authorization", "Bearer $tk")
                sedeActual()?.let { parameter("sede", it) }
            }
        }
        if (!r.ok) return null to r.error
        return (r.cuerpo?.let { parsearGastos(it) } ?: return null to "Respuesta inesperada del servidor.") to null
    }

    internal fun parsearGastos(o: JsonObject): GastosYCategorias {
        fun lista(k: String) = ((o["categorias"] as? JsonObject)?.get(k) as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return GastosYCategorias(
            gastos = (o["gastos"] as? JsonArray).orEmpty().mapNotNull { e ->
                val g = e as? JsonObject ?: return@mapNotNull null
                GastoFijo(
                    id = g.str("id") ?: return@mapNotNull null,
                    nombre = g.str("nombre") ?: "",
                    categoria = g.str("categoria") ?: "Gasto fijo",
                    monto = g.dbl("monto") ?: 0.0,
                    frecuencia = g.str("frecuencia") ?: "Mensual",
                    diaCobro = g.int("diaCobro"),
                    fechaUnica = g.str("fechaUnica"),
                    activo = g.bool("activo") ?: true,
                    pagado = g.bool("pagado") ?: false,
                    yaEnCaja = g.bool("yaEnCaja") ?: false,
                    unicoCerrado = g.bool("unicoCerrado") ?: false,
                    proximo = g.str("proximo"),
                )
            },
            catEgreso = lista("egreso"),
            catIngreso = lista("ingreso"),
            catGastoFijo = lista("gastoFijo"),
        )
    }

    private fun cuerpoGasto(nombre: String, categoria: String, monto: Double, frecuencia: String, diaCobro: Int?, fechaUnica: String?) =
        buildJsonObject {
            put("nombre", nombre); put("categoria", categoria); put("monto", monto); put("frecuencia", frecuencia)
            diaCobro?.let { put("dia_cobro", it) }
            fechaUnica?.let { put("fecha_unica", it) }
        }

    suspend fun crearGasto(nombre: String, categoria: String, monto: Double, frecuencia: String, diaCobro: Int?): RespuestaFinanzas {
        val base = cuerpoGasto(nombre, categoria, monto, frecuencia, diaCobro, null)
        return post("/api/staff/gastos-recurrentes", JsonObject(base + (sedeActual()?.let { mapOf("sede" to JsonPrimitive(it)) } ?: emptyMap())))
    }

    suspend fun editarGasto(id: String, nombre: String, categoria: String, monto: Double, frecuencia: String, diaCobro: Int?, fechaUnica: String?): RespuestaFinanzas =
        patch("/api/staff/gastos-recurrentes", JsonObject(cuerpoGasto(nombre, categoria, monto, frecuencia, diaCobro, fechaUnica) + ("id" to JsonPrimitive(id))))

    suspend fun borrarGasto(id: String): RespuestaFinanzas = llamar { tk ->
        http.delete("${Supabase.SITE_URL}/api/staff/gastos-recurrentes") {
            header("Authorization", "Bearer $tk"); parameter("id", id)
        }
    }

    /** "⚠ Registrar": el egreso del período vigente que se quedó sin entrar a la caja. */
    suspend fun registrarGasto(id: String): RespuestaFinanzas =
        post("/api/staff/gastos-recurrentes", buildJsonObject { put("accion", "registrar"); put("id", id) })

    // ── Por cobrar ──────────────────────────────────────────────────────────

    /**
     * Consultas y evaluaciones ATENDIDAS que nadie cobró (últimos 60 días), como
     * el bloque "Por cobrar" de la web. El cobro va por /api/staff/cita/cobrar
     * ([AgendaRepo.cobrarCita]).
     */
    suspend fun porCobrar(hoyIso: String): List<PendienteCobro> {
        val desde = sumarDiasIso(hoyIso, -60)
        return Supabase.client.postgrest["citas"]
            .select(Columns.raw("id, fecha, hora, tipo, costo, paciente_id, paciente:pacientes(nombre), terapeuta:terapeutas(nombre)")) {
                filter {
                    eq("estado", "Completada")
                    exact("pagada_at", null)
                    gt("costo", 0)
                    isIn("tipo", listOf("Consulta", "Evaluación"))
                    gte("fecha", desde)
                }
                order("fecha", Order.DESCENDING)
                limit(100)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                PendienteCobro(
                    citaId = o.str("id") ?: return@mapNotNull null,
                    fecha = o.str("fecha") ?: "",
                    hora = o.str("hora")?.take(5),
                    tipo = o.str("tipo") ?: "Cita",
                    costo = o.dbl("costo") ?: 0.0,
                    paciente = (o["paciente"] as? JsonObject)?.str("nombre") ?: "Paciente",
                    pacienteId = o.str("paciente_id"),
                    profesional = (o["terapeuta"] as? JsonObject)?.str("nombre"),
                )
            }
    }
}
