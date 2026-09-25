package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.saniape.app.data.Supabase

/**
 * Los hallazgos del odontograma de un paciente.
 *
 * SOLO ODONTOLOGÍA. Nada de esto se monta en clínicas de otros rubros: la
 * pestaña ni aparece (ver `esOdontologia` en la ficha), así que una fisio nunca
 * llega hasta acá. Leer directo a Supabase con la RLS del staff, igual que el
 * resto de catálogos de la app.
 *
 * Las escrituras son directas y no pasan por /api/staff: no tocan dinero ni
 * contadores, solo marcan lo que el odontólogo ve en la boca. El presupuesto se
 * calcula en memoria (`agruparPresupuesto`) y solo se materializa cuando se
 * crea el tratamiento, que sí va por el endpoint de siempre.
 */
object OdontogramaRepo {

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.bool(k: String): Boolean =
        (this[k] as? JsonPrimitive)?.content?.lowercase() == "true"

    private fun JsonObject.entero(k: String): Int =
        (this[k] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0

    // ── Caché y fusión de pedidos (rendimiento de la ficha dental) ────────────

    /**
     * El catálogo de hallazgos cambia rarísima vez (lo edita la clínica en la
     * web) y lo piden el odontograma, "Piezas del plan" de CADA tratamiento,
     * "¿Qué se le hizo hoy?" y la revisión previa. Se guarda unos minutos en
     * memoria; se borra al cambiar de clínica o salir ([limpiarCache]).
     */
    private var cacheCatalogo: Pair<Long, List<HallazgoDental>>? = null
    private const val TTL_CATALOGO_MS = 5 * 60_000L

    /** Sube con cada escritura: lo pedido antes de escribir no se reutiliza después. */
    private var generacion = 0

    private val candado = Mutex()
    private val enVuelo = mutableMapOf<String, CompletableDeferred<Any?>>()

    /** Borra lo guardado (cambio de clínica / cierre de sesión). */
    fun limpiarCache() { cacheCatalogo = null; generacion++ }

    /**
     * Si ya hay un pedido idéntico EN VUELO, espera ese en vez de lanzar otro.
     * Si quien lo lanzó se cancela (salió de la pantalla), el que esperaba lo
     * pide por su cuenta: nunca queda colgado.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> coalescer(clave: String, pedir: suspend () -> T): T {
        var propio = false
        val d = candado.withLock {
            enVuelo[clave] ?: CompletableDeferred<Any?>().also { enVuelo[clave] = it; propio = true }
        }
        if (!propio) {
            return try { d.await() as T } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                pedir()
            }
        }
        try {
            val r = pedir()
            d.complete(r)
            return r
        } catch (e: Throwable) {
            d.completeExceptionally(e)
            throw e
        } finally {
            withContext(NonCancellable) { candado.withLock { if (enVuelo[clave] === d) enVuelo.remove(clave) } }
        }
    }

    /**
     * El catálogo de hallazgos de la clínica (Caries, Ausente, Sarro…).
     *
     * Trae también los INACTIVOS: un hallazgo que la clínica desactivó hoy
     * sigue existiendo en bocas marcadas antes, y sin su nombre y color el
     * diagrama se pintaría con huecos.
     */
    suspend fun catalogo(forzar: Boolean = false): List<HallazgoDental> {
        val ahora = Clock.System.now().toEpochMilliseconds()
        if (!forzar) cacheCatalogo?.let { (t, lista) -> if (ahora - t < TTL_CATALOGO_MS) return lista }
        val lista = coalescer("catalogo#$generacion") { catalogoRed() }
        // Vacío casi siempre es un fallo de red: no se guarda, así el próximo reintenta.
        if (lista.isNotEmpty()) cacheCatalogo = ahora to lista
        return lista
    }

    private suspend fun catalogoRed(): List<HallazgoDental> = try {
        Supabase.client.postgrest["hallazgos_dentales"]
            .select(Columns.list("id, clinica_id, nombre, color, procedimiento_id, marca_ausente, orden, estado, por_boca")) {
                order("orden", Order.ASCENDING)
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                HallazgoDental(
                    id = id,
                    nombre = o.str("nombre") ?: "",
                    color = o.str("color") ?: "#dc2626",
                    clinicaId = o.str("clinica_id"),
                    procedimientoId = o.str("procedimiento_id"),
                    marcaAusente = o.bool("marca_ausente"),
                    orden = o.entero("orden"),
                    estado = o.str("estado") ?: "Activo",
                    porBoca = o.bool("por_boca"),
                )
            }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }

    /**
     * Lo marcado en la boca de un paciente.
     *
     * Pedidos simultáneos del mismo paciente se FUSIONAN en uno: la ficha dental
     * monta "Piezas del plan" en cada tarjeta de tratamiento y, al recargar,
     * todas lo piden a la vez (antes: N tratamientos = N consultas iguales).
     * Solo se comparte lo que está EN VUELO (nunca un resultado viejo), y una
     * escritura de este repo abre una generación nueva: lo pedido después de
     * marcar no se cuelga de una consulta que salió antes de marcar.
     */
    suspend fun hallazgos(pacienteId: String): List<DienteHallazgo> =
        hallazgosONull(pacienteId) ?: emptyList()

    /** Igual que [hallazgos], pero null si falló la red (para no confundir "falló" con "no hay nada"). */
    suspend fun hallazgosONull(pacienteId: String): List<DienteHallazgo>? =
        coalescer("hallazgos:$pacienteId#$generacion") { hallazgosRed(pacienteId) }

    private suspend fun hallazgosRed(pacienteId: String): List<DienteHallazgo>? = try {
        Supabase.client.postgrest["dientes_hallazgos"]
            .select(Columns.list("id, paciente_id, diente, hallazgo_id, superficies, estado, cita_id, tratamiento_id, notas, fecha, sesion_id, diente_hasta")) {
                filter { eq("paciente_id", pacienteId) }
                order("fecha", Order.DESCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                DienteHallazgo(
                    id = id,
                    pacienteId = o.str("paciente_id") ?: pacienteId,
                    diente = o.str("diente") ?: "",
                    hallazgoId = o.str("hallazgo_id") ?: "",
                    // `superficies` es un array de Postgres; puede venir null.
                    superficies = (o["superficies"] as? kotlinx.serialization.json.JsonArray)
                        ?.mapNotNull { it.jsonPrimitive.content.takeIf { c -> c.isNotBlank() } }
                        ?.takeIf { it.isNotEmpty() },
                    estado = o.str("estado") ?: "Pendiente",
                    citaId = o.str("cita_id"),
                    tratamientoId = o.str("tratamiento_id"),
                    notas = o.str("notas"),
                    fecha = o.str("fecha") ?: "",
                    sesionId = o.str("sesion_id"),
                    dienteHasta = o.str("diente_hasta"),
                )
            }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    /**
     * Marca un hallazgo en un diente.
     *
     * NO se manda `clinica_id`: el DEFAULT de la tabla es `get_clinica_id()` y
     * ponerlo a mano rompe la RLS (regla del proyecto, documentada en la web).
     *
     * [citaId] ata el hallazgo a la atención en que se encontró, para que quede
     * fechado y con su responsable.
     */
    suspend fun agregar(
        pacienteId: String,
        diente: String,
        hallazgoId: String,
        superficies: List<String>? = null,
        citaId: String? = null,
        notas: String? = null,
    ): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            put("diente", diente)
            put("hallazgo_id", hallazgoId)
            if (!superficies.isNullOrEmpty()) {
                putJsonArray("superficies") { superficies.forEach { add(JsonPrimitive(it)) } }
            }
            if (citaId != null) put("cita_id", citaId)
            if (!notas.isNullOrBlank()) put("notas", notas)
        })
        true
    } catch (_: Exception) { false }.also { generacion++ }

    /** Lo que se marca de una vez (dictado). */
    data class NuevoHallazgo(val diente: String, val hallazgoId: String, val superficies: List<String>?)

    /**
     * Varios hallazgos en UN insert (todo o nada). Antes el dictado hacía un
     * insert por pieza en serie: 10 piezas = 10 viajes. Todas las filas llevan
     * las MISMAS claves (null explícito): PostgREST arma las columnas del lote
     * con ellas. Sin clinica_id, como siempre (DEFAULT get_clinica_id()).
     */
    suspend fun agregarVarios(pacienteId: String, lote: List<NuevoHallazgo>, citaId: String? = null): Boolean {
        if (lote.isEmpty()) return true
        return try {
            Supabase.client.postgrest["dientes_hallazgos"].insert(lote.map { h ->
                buildJsonObject {
                    put("paciente_id", pacienteId)
                    put("diente", h.diente)
                    put("hallazgo_id", h.hallazgoId)
                    if (!h.superficies.isNullOrEmpty()) putJsonArray("superficies") { h.superficies.forEach { add(JsonPrimitive(it)) } }
                    else put("superficies", JsonNull)
                    if (citaId != null) put("cita_id", citaId) else put("cita_id", JsonNull)
                }
            })
            true
        } catch (_: Exception) { false }.also { generacion++ }
    }

    /**
     * Pasa un hallazgo a Realizado, o lo devuelve a Pendiente.
     *
     * Revertir importa: el odontólogo marca "listo" por error y sin vuelta
     * atrás tendría que borrarlo y volver a marcarlo, perdiendo la fecha.
     */
    suspend fun cambiarEstado(id: String, estado: String): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"]
            .update({
                set("estado", estado)
                // Devuelto a pendiente: se suelta de la sesión. Si quedaba atada,
                // al re-completar esa sesión volvía a marcarse sola como hecha (web igual).
                if (estado == "Pendiente") setToNull("sesion_id")
            }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }.also { generacion++ }

    /**
     * Cambia las caras de un hallazgo ya registrado: marcar otra caries en la
     * misma pieza SUMA la cara al registro pendiente (una sola caries "MO", se
     * cobra una vez). Orden fijo O M D V L. Vacío = pieza entera.
     */
    suspend fun actualizarSuperficies(id: String, caras: List<String>): Boolean = try {
        val orden = ordenarCaras(caras)
        Supabase.client.postgrest["dientes_hallazgos"]
            .update({
                if (orden.isEmpty()) setToNull("superficies")
                else set("superficies", orden)
            }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }.also { generacion++ }

    /** Nota de ESTA pieza ("caries profunda, riesgo pulpar"): la ve quien atienda la próxima sesión. */
    suspend fun actualizarNotas(id: String, notas: String?): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"]
            .update({ set("notas", notas?.trim()?.ifBlank { null }) }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }.also { generacion++ }

    /**
     * Nombre de cada servicio por id: al marcar una pieza como hecha, su
     * procedimiento ("Resina / restauración") se suma a lo realizado en la sesión.
     */
    suspend fun nombresProcedimientos(): Map<String, String> = try {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.list("id, nombre"))
            .decodeList<JsonObject>()
            .mapNotNull { o -> (o.str("id") ?: return@mapNotNull null) to (o.str("nombre") ?: "") }
            .toMap()
    } catch (_: Exception) { emptyMap() }

    /** Quita un hallazgo (se marcó el diente equivocado). */
    suspend fun borrar(id: String): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"].delete { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }.also { generacion++ }

    /**
     * Los servicios del tarifario que pueden cobrarse por un hallazgo.
     *
     * Solo los ACTIVOS: un servicio dado de baja no debe aparecer en un
     * presupuesto nuevo. Se filtra por especialidad cuando se sabe cuál es, para
     * no mezclar el tarifario de odontología con el de otra rama de la clínica.
     */
    suspend fun procedimientos(especialidadId: String? = null): List<ProcedimientoRef> = try {
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.list(
                "id, nombre, precio, precio_paquete, especialidad_id, modo_cobro, unidad_label, precio_unitario_sugerido, " +
                    // El tarifario da cuántas sesiones tiene un servicio "por sesiones".
                    "tarifarios:tarifario_paquetes(id, cantidad_sesiones, precio_total)",
            )) {
                filter {
                    eq("estado", "Activo")
                    if (especialidadId != null) eq("especialidad_id", especialidadId)
                }
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { o ->
                val id = o.str("id") ?: return@mapNotNull null
                ProcedimientoRef(
                    id = id,
                    nombre = o.str("nombre") ?: "",
                    precio = o.str("precio")?.toDoubleOrNull() ?: 0.0,
                    precioPaquete = o.str("precio_paquete")?.toDoubleOrNull(),
                    especialidadId = o.str("especialidad_id"),
                    usaSesiones = false,
                    tarifarios = (o["tarifarios"] as? kotlinx.serialization.json.JsonArray).orEmpty()
                        .mapNotNull { t ->
                            val to = t as? JsonObject ?: return@mapNotNull null
                            TarifarioRef(
                                id = to.str("id") ?: return@mapNotNull null,
                                cantidadSesiones = to.str("cantidad_sesiones")?.toIntOrNull() ?: return@mapNotNull null,
                                precioTotal = to.str("precio_total")?.toDoubleOrNull() ?: 0.0,
                            )
                        },
                    modoCobro = o.str("modo_cobro"),
                    unidadLabel = o.str("unidad_label"),
                    precioUnitarioSugerido = o.str("precio_unitario_sugerido")?.toDoubleOrNull(),
                )
            }
    } catch (_: Exception) { emptyList() }

    /**
     * Le asigna un servicio a un hallazgo del CATÁLOGO que no tenía (p. ej.
     * "Fractura" sin servicio). Es de la clínica, no del paciente: a partir de
     * ahí toda fractura se presupuesta con ese servicio. Igual que en la web.
     */
    suspend fun vincularServicio(hallazgoId: String, procedimientoId: String): Boolean = try {
        Supabase.client.postgrest["hallazgos_dentales"]
            .update({ set("procedimiento_id", procedimientoId) }) { filter { eq("id", hallazgoId) } }
        true
    } catch (_: Exception) { false }.also { if (it) cacheCatalogo = null; generacion++ }

    /**
     * Lo que hace falta para decidir si la ficha de un paciente muestra la
     * pestaña 🦷 en una clínica MIXTA (`pacienteEsDental`). Solo se pide ahí:
     * en una clínica solo dental o sin odontología la respuesta ya se sabe.
     *
     * Tres lecturas chicas en paralelo: las especialidades de sus citas
     * (incluidas las pendientes — su primer diagnóstico dental todavía no es
     * un tratamiento), si ya tiene algo marcado, y las especialidades de quien
     * mira (el dentista tiene que poder empezarle un odontograma).
     */
    suspend fun datosDentalesPaciente(pacienteId: String, miTerapeutaId: String?): DatosDentalesPaciente =
        kotlinx.coroutines.coroutineScope {
            val citasD = async {
                runCatching {
                    Supabase.client.postgrest["citas"]
                        .select(Columns.list("especialidad_id")) {
                            filter { eq("paciente_id", pacienteId); neq("estado", "Cancelada") }
                            limit(200)
                        }.decodeList<JsonObject>().mapNotNull { it.str("especialidad_id") }.distinct()
                }.getOrDefault(emptyList())
            }
            val hallazgosD = async {
                runCatching {
                    Supabase.client.postgrest["dientes_hallazgos"]
                        .select(Columns.list("id")) { filter { eq("paciente_id", pacienteId) }; limit(1) }
                        .decodeList<JsonObject>().isNotEmpty()
                }.getOrDefault(false)
            }
            val mirandoD = async {
                if (miTerapeutaId == null) emptyList() else runCatching {
                    Supabase.client.postgrest["terapeuta_especialidades"]
                        .select(Columns.list("especialidad_id")) { filter { eq("terapeuta_id", miTerapeutaId) } }
                        .decodeList<JsonObject>().mapNotNull { it.str("especialidad_id") }
                }.getOrDefault(emptyList())
            }
            DatosDentalesPaciente(citasD.await(), hallazgosD.await(), mirandoD.await())
        }
}

/** Ver [OdontogramaRepo.datosDentalesPaciente]. */
data class DatosDentalesPaciente(
    val especialidadesDeCitas: List<String> = emptyList(),
    val tieneHallazgos: Boolean = false,
    val especialidadesDeQuienMira: List<String> = emptyList(),
)
