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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura
import pe.saniape.app.data.offline.enviarOEncolarDetalle
import kotlin.math.roundToInt

// ─── Filas de Supabase → modelos (puras, testeables) ─────────────────────────

private fun JsonObject.txt(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
private fun JsonObject.ent(k: String): Int? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.roundToInt()
private fun JsonObject.si(k: String): Boolean? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toBooleanStrictOrNull()
private fun JsonObject.textos(k: String): List<String> =
    (this[k] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } }
private fun JsonObject.enteros(k: String): List<Int> =
    (this[k] as? JsonArray).orEmpty().mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.roundToInt() }

/** Los joins de PostgREST llegan como objeto o como arreglo de uno. */
private fun JsonElement?.uno(): JsonObject? = when (this) {
    is JsonObject -> this
    is JsonArray -> firstOrNull() as? JsonObject
    else -> null
}

/** Fila de `ejercicios` → EjercicioBiblioteca. Sin id o sin nombre se descarta. */
internal fun aEjercicioBiblioteca(o: JsonObject): EjercicioBiblioteca? {
    val id = o.txt("id") ?: return null
    return EjercicioBiblioteca(
        id = id,
        slug = o.txt("slug").orEmpty(),
        nombre = o.txt("nombre") ?: return null,
        zona = o.txt("zona").orEmpty(),
        zonaNombre = o.txt("zona_nombre"),
        zonaOrden = o.ent("zona_orden"),
        objetivo = o.txt("objetivo"),
        posicion = o.txt("posicion"),
        materiales = o.textos("materiales"),
        condiciones = o.textos("condiciones"),
        dificultad = o.txt("dificultad"),
        pasos = o.textos("pasos"),
        series = o.ent("series"),
        repeticiones = o.ent("repeticiones"),
        descansoSeg = o.ent("descanso_seg"),
        diasSemana = o.ent("dias_semana"),
        gifUrl = o.txt("gif_url"),
        posturaInicialUrl = o.txt("postura_inicial_url"),
        posturaFinalUrl = o.txt("postura_final_url"),
        estadoRevision = o.txt("estado_revision") ?: "pendiente",
        clinicaId = o.txt("clinica_id"),
    )
}

private fun aItemPlan(o: JsonObject): ItemPlanEjercicios? {
    val id = o.txt("id") ?: return null
    val e = o["ejercicio"].uno()
    return ItemPlanEjercicios(
        id = id,
        planId = o.txt("plan_id").orEmpty(),
        ejercicioId = o.txt("ejercicio_id").orEmpty(),
        ejercicioNombre = o.txt("ejercicio_nombre") ?: e?.txt("nombre").orEmpty(),
        orden = o.ent("orden") ?: 0,
        series = o.ent("series") ?: 1,
        repeticiones = o.ent("repeticiones"),
        sostenerSeg = o.ent("sostener_seg"),
        descansoSeg = o.ent("descanso_seg"),
        vecesAlDia = o.ent("veces_al_dia") ?: 1,
        dias = o.enteros("dias"),
        lado = o.txt("lado"),
        carga = o.txt("carga"),
        indicaciones = o.txt("indicaciones"),
        dolorMaximo = o.ent("dolor_maximo"),
        activo = o.si("activo") ?: true,
        createdAt = o.txt("created_at").orEmpty(),
        ejercicio = e?.txt("id")?.let { eid ->
            EjercicioDeItem(
                id = eid,
                nombre = e.txt("nombre"),
                zonaNombre = e.txt("zona_nombre"),
                objetivo = e.txt("objetivo"),
                posicion = e.txt("posicion"),
                materiales = e.textos("materiales"),
                pasos = e.textos("pasos"),
                gifUrl = e.txt("gif_url"),
                posturaInicialUrl = e.txt("postura_inicial_url"),
                posturaFinalUrl = e.txt("postura_final_url"),
                estadoRevision = e.txt("estado_revision"),
            )
        },
    )
}

/**
 * Fila de `planes_ejercicios` (con su sesión y sus ejercicios) → PlanEjercicios.
 * Los ejercicios van por `orden` y, a igual orden, por cuándo se agregaron.
 */
internal fun aPlanEjercicios(o: JsonObject): PlanEjercicios? {
    val id = o.txt("id") ?: return null
    val s = o["sesion"].uno()
    return PlanEjercicios(
        id = id,
        pacienteId = o.txt("paciente_id").orEmpty(),
        tratamientoId = o.txt("tratamiento_id"),
        sesionId = o.txt("sesion_id"),
        terapeutaId = o.txt("terapeuta_id"),
        momento = if (o.txt("momento") == "alta") "alta" else "sesion",
        sesion = s?.txt("id")?.let { sid -> SesionDePlan(sid, s.ent("numero") ?: 0, s.txt("fecha").orEmpty()) },
        titulo = o.txt("titulo").orEmpty(),
        motivo = o.txt("motivo"),
        indicacionesGenerales = o.txt("indicaciones_generales"),
        precauciones = o.txt("precauciones"),
        fechaInicio = o.txt("fecha_inicio").orEmpty(),
        fechaFin = o.txt("fecha_fin"),
        estado = o.txt("estado") ?: "activo",
        visiblePaciente = o.si("visible_paciente") ?: true,
        token = o.txt("token"),
        creadoPorNombre = o.txt("creado_por_nombre"),
        createdAt = o.txt("created_at").orEmpty(),
        updatedAt = o.txt("updated_at").orEmpty(),
        items = (o["items"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::aItemPlan) }
            .sortedWith(compareBy<ItemPlanEjercicios> { it.orden }.thenBy { it.createdAt }),
    )
}

/** Los vigentes arriba; el resto, del más reciente al más viejo (como `cargarPlanesEjercicios`). */
internal fun ordenarPlanes(planes: List<PlanEjercicios>): List<PlanEjercicios> =
    planes.sortedWith(compareByDescending<PlanEjercicios> { it.estado == "activo" }.thenByDescending { it.createdAt })

internal fun aRegistroEjercicio(o: JsonObject): RegistroEjercicio? {
    val itemId = o.txt("item_id") ?: return null
    val fecha = o.txt("fecha")?.take(10) ?: return null
    return RegistroEjercicio(itemId, fecha, o.si("completado") ?: false, o.ent("dolor"), o.txt("comentario"))
}

internal fun aSesionDestino(o: JsonObject): SesionDestino? {
    val id = o.txt("id") ?: return null
    return SesionDestino(id, o.ent("numero") ?: 0, o.txt("fecha").orEmpty(), o.txt("estado").orEmpty(), o.txt("tratamiento_id"))
}

// ─── Cuerpos de POST /api/staff/ejercicios/plan (puros, testeables) ──────────

/** Lo que el fisio dejó en "Ajustar ejercicio" (los números van como los escribió). */
data class DosisEdicion(
    val series: String,
    val repeticiones: String,
    val sostenerSeg: String,
    val descansoSeg: String,
    val vecesAlDia: Int,
    val dias: List<Int>,
    val lado: String?,
    val carga: String,
    val dolorMaximo: Int?,
    val indicaciones: String,
)

/** Número si lo es; si no, el texto tal cual: el servidor valida y dice por qué no. Vacío = null. */
private fun numeroONull(texto: String): JsonPrimitive {
    val t = texto.trim()
    if (t.isEmpty()) return JsonNull
    return t.toIntOrNull()?.let { JsonPrimitive(it) } ?: JsonPrimitive(t)
}

/** El destino de `agregar` y `copiar`: la sesión (hasta la siguiente) o el fin del tratamiento. */
private fun JsonObjectBuilder.destino(d: DestinoEjercicios) {
    put("momento", d.momento)
    d.sesionId?.takeIf { it.isNotEmpty() }?.let { put("sesionId", it) }
    d.tratamientoId?.takeIf { it.isNotEmpty() }?.let { put("tratamientoId", it) }
}

internal fun cuerpoAgregar(pacienteId: String, ejercicioIds: List<String>, d: DestinoEjercicios): JsonObject = buildJsonObject {
    put("accion", "agregar")
    put("pacienteId", pacienteId)
    putJsonArray("ejercicioIds") { ejercicioIds.distinct().forEach { add(JsonPrimitive(it)) } }
    destino(d)
}

internal fun cuerpoCopiar(pacienteId: String, desdePlanId: String, d: DestinoEjercicios): JsonObject = buildJsonObject {
    put("accion", "copiar")
    put("pacienteId", pacienteId)
    put("desdePlanId", desdePlanId)
    destino(d)
}

/** Manda la dosis ENTERA del formulario (como la web); el servidor sanea y valida. */
internal fun cuerpoEditarItem(itemId: String, d: DosisEdicion): JsonObject = buildJsonObject {
    put("accion", "editarItem")
    put("itemId", itemId)
    put("series", numeroONull(d.series))
    put("repeticiones", numeroONull(d.repeticiones))
    put("sostenerSeg", numeroONull(d.sostenerSeg))
    put("descansoSeg", numeroONull(d.descansoSeg))
    put("vecesAlDia", d.vecesAlDia)
    putJsonArray("dias") { d.dias.distinct().sorted().forEach { add(JsonPrimitive(it)) } }
    put("lado", d.lado?.takeIf { it.isNotEmpty() }?.let { JsonPrimitive(it) } ?: JsonNull)
    put("carga", d.carga)
    put("dolorMaximo", d.dolorMaximo?.let { JsonPrimitive(it) } ?: JsonNull)
    put("indicaciones", d.indicaciones)
}

internal fun cuerpoQuitarItem(itemId: String): JsonObject = buildJsonObject {
    put("accion", "quitarItem")
    put("itemId", itemId)
}

internal fun cuerpoEditarPlan(
    planId: String, titulo: String, motivo: String, indicacionesGenerales: String, precauciones: String, visiblePaciente: Boolean,
): JsonObject = buildJsonObject {
    put("accion", "editarPlan")
    put("planId", planId)
    put("titulo", titulo)
    put("motivo", motivo)
    put("indicacionesGenerales", indicacionesGenerales)
    put("precauciones", precauciones)
    put("visiblePaciente", visiblePaciente)
}

/** [accion]: `estado` (con [estado]) · `compartir` · `revocar`. */
internal fun cuerpoDePlan(accion: String, planId: String, estado: String? = null): JsonObject = buildJsonObject {
    put("accion", accion)
    put("planId", planId)
    estado?.let { put("estado", it) }
}

/** El `token` que devuelve `compartir` (64 hex). null si no vino. */
internal fun tokenDeRespuesta(cuerpo: JsonObject?): String? = cuerpo?.txt("token")

/** El enlace público del plan: lo abre el paciente, sin cuenta. */
fun urlPlanEjercicios(token: String, sitio: String = Supabase.SITE_URL): String = "${sitio.trimEnd('/')}/ejercicios/$token"

/**
 * Ejercicios de apoyo del paciente (staff): biblioteca, planes y lo que el
 * paciente marcó como hecho. Gemelo de `lib/ejercicios-db.ts` (lecturas) y de
 * `postAccionStaff('/api/staff/ejercicios/plan')` (escrituras).
 *
 * LECTURAS directas a Supabase con la sesión del staff (la RLS acota a la clínica
 * y deja ver la biblioteca global), con las mismas columnas que la web.
 * ESCRITURAS por POST /api/staff/ejercicios/plan — toda la regla vive en
 * lib/ejercicios-acciones.ts — con la cola offline: todas las acciones son
 * seguras de repetir. `clinica_id` NUNCA se manda.
 */
object EjerciciosRepo {

    private const val RUTA = "/api/staff/ejercicios/plan"

    private const val COLS_PLAN =
        "id, paciente_id, tratamiento_id, sesion_id, terapeuta_id, momento, titulo, motivo, indicaciones_generales, precauciones, " +
            "sesion:sesiones(id, numero, fecha), " +
            "fecha_inicio, fecha_fin, estado, visible_paciente, token, creado_por_nombre, created_at, updated_at, " +
            "items:plan_ejercicios_items(id, plan_id, ejercicio_id, ejercicio_nombre, orden, series, repeticiones, sostener_seg, " +
            "descanso_seg, veces_al_dia, dias, lado, carga, indicaciones, dolor_maximo, activo, created_at, " +
            "ejercicio:ejercicios(id, slug, nombre, zona_nombre, objetivo, posicion, materiales, pasos, gif_url, postura_inicial_url, postura_final_url, estado_revision))"

    private val http = crearHttpClient()

    // ── Biblioteca ───────────────────────────────────────────────────────────

    /** La biblioteca ya pedida y de quién es (usuario + clínica): cambia poco, se pide una vez. */
    private var enMemoria: Triple<String, Long, List<EjercicioBiblioteca>>? = null
    private const val VIGENCIA_BIBLIOTECA_MS = 10L * 60 * 1000

    /**
     * La biblioteca entera (global + propios de la clínica), ~600 filas livianas.
     * Se filtra en el teléfono (`filtrarBiblioteca`): cada chip responde al
     * instante, sin un viaje por filtro. Se guarda 10 min en memoria, por usuario
     * y [clinicaId] (los ejercicios propios son de cada clínica). null = falló la red.
     */
    suspend fun biblioteca(clinicaId: String): List<EjercicioBiblioteca>? {
        val clave = "${Supabase.client.auth.currentUserOrNull()?.id.orEmpty()}|$clinicaId"
        val ahora = Clock.System.now().toEpochMilliseconds()
        enMemoria?.takeIf { it.first == clave && ahora - it.second < VIGENCIA_BIBLIOTECA_MS }?.let { return it.third }
        return try {
            Supabase.client.postgrest["ejercicios"].select(Columns.raw(COLS_EJERCICIO)) {
                filter {
                    eq("activo", true)
                    neq("estado_revision", "rechazado")
                }
                order("zona_orden", Order.ASCENDING)
                order("nombre", Order.ASCENDING)
                limit(2000)
            }.decodeList<JsonObject>().mapNotNull { aEjercicioBiblioteca(it) }
                .also { if (it.isNotEmpty()) enMemoria = Triple(clave, ahora, it) }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    // ── Planes del paciente ──────────────────────────────────────────────────

    data class DatosEjercicios(
        val planes: List<PlanEjercicios>,
        val registros: List<RegistroEjercicio>,
        /** Sesiones de los tratamientos de fisioterapia del paciente: de ahí sale "Indicar para". */
        val sesiones: List<SesionDestino>,
    )

    /**
     * Los planes del paciente (los vigentes primero) con sus ejercicios, lo que
     * marcó como hecho en los últimos 60 días y las sesiones de [tratamientoIds].
     * Tres consultas chicas en PARALELO. null = falló la red (no "vacío").
     */
    suspend fun cargar(pacienteId: String, tratamientoIds: List<String>, hoy: String): DatosEjercicios? = try {
        coroutineScope {
            val pl = async {
                Supabase.client.postgrest["planes_ejercicios"].select(Columns.raw(COLS_PLAN)) {
                    filter { eq("paciente_id", pacienteId) }
                    order("created_at", Order.DESCENDING)
                }.decodeList<JsonObject>()
            }
            val rg = async {
                Supabase.client.postgrest["plan_ejercicios_registros"]
                    .select(Columns.raw("item_id, fecha, completado, dolor, comentario")) {
                        filter {
                            eq("paciente_id", pacienteId)
                            gte("fecha", sumarDiasIso(hoy, -VENTANA_CUMPLIMIENTO))
                        }
                        order("fecha", Order.DESCENDING)
                        limit(2000)
                    }.decodeList<JsonObject>()
            }
            val se = async {
                if (tratamientoIds.isEmpty()) emptyList() else
                    Supabase.client.postgrest["sesiones"].select(Columns.raw("id, numero, fecha, estado, tratamiento_id")) {
                        filter { isIn("tratamiento_id", tratamientoIds) }
                        order("fecha", Order.DESCENDING)
                        limit(500)
                    }.decodeList<JsonObject>()
            }
            DatosEjercicios(
                planes = ordenarPlanes(pl.await().mapNotNull { aPlanEjercicios(it) }),
                registros = rg.await().mapNotNull { aRegistroEjercicio(it) },
                sesiones = se.await().mapNotNull { aSesionDestino(it) },
            )
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    // ── Escrituras ───────────────────────────────────────────────────────────

    private suspend fun accion(cuerpo: JsonObject): ResultadoEscritura =
        enviarOEncolarDetalle("ejercicios:plan:${cuerpo.txt("accion")}", RUTA, cuerpo)

    /** Suma al plan del destino (lo crea si no hay) con la dosis sugerida. No duplica. */
    suspend fun agregar(pacienteId: String, ejercicioIds: List<String>, destino: DestinoEjercicios): ResultadoEscritura =
        accion(cuerpoAgregar(pacienteId, ejercicioIds, destino))

    /** "↩ Repetir los de la sesión anterior": misma dosis e indicaciones. */
    suspend fun copiar(pacienteId: String, desdePlanId: String, destino: DestinoEjercicios): ResultadoEscritura =
        accion(cuerpoCopiar(pacienteId, desdePlanId, destino))

    suspend fun editarItem(itemId: String, dosis: DosisEdicion): ResultadoEscritura = accion(cuerpoEditarItem(itemId, dosis))

    /** Lo apaga (`activo = false`): lo que el paciente ya registró se conserva. */
    suspend fun quitarItem(itemId: String): ResultadoEscritura = accion(cuerpoQuitarItem(itemId))

    suspend fun editarPlan(
        planId: String, titulo: String, motivo: String, indicacionesGenerales: String, precauciones: String, visiblePaciente: Boolean,
    ): ResultadoEscritura = accion(cuerpoEditarPlan(planId, titulo, motivo, indicacionesGenerales, precauciones, visiblePaciente))

    /** [estado]: activo | pausado | finalizado. Reactivar exige que ese tratamiento no tenga otro vigente. */
    suspend fun cambiarEstado(planId: String, estado: String): ResultadoEscritura = accion(cuerpoDePlan("estado", planId, estado))

    /** Apaga el enlace público: quien lo tenga deja de ver el plan. */
    suspend fun revocar(planId: String): ResultadoEscritura = accion(cuerpoDePlan("revocar", planId))

    /**
     * El enlace del plan para mandarlo por WhatsApp (el servidor lo crea una vez).
     * DIRECTO, sin cola offline: hace falta el `token` de la respuesta
     * ([tokenDeRespuesta] sobre `cuerpo`) y sin señal no hay nada que compartir.
     */
    suspend fun compartir(planId: String): ResultadoEscritura {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken
            ?: return ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401))
        return try {
            pe.saniape.app.ui.conIndicador {
                val resp = http.post("${Supabase.SITE_URL}$RUTA") {
                    header("Authorization", "Bearer $tk")
                    contentType(ContentType.Application.Json)
                    setBody(cuerpoDePlan("compartir", planId).toString())
                }
                AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) {
            ResultadoEscritura(registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"))
        }
    }
}
