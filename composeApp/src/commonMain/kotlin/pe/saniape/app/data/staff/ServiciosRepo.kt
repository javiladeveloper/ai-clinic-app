package pe.saniape.app.data.staff

import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase
import pe.saniape.app.data.crearHttpClient
import pe.saniape.app.data.offline.RechazoServidor
import pe.saniape.app.data.offline.ResultadoEscritura

// ─────────────────────────────────────────────────────────────────────────────
// Servicios (catálogo de procedimientos). Gemelo de app/(app)/procedimientos en
// la web. Lectura directa (RLS por clínica); escrituras por /api/staff/servicio
// (lib/servicios-acciones.ts): el servicio, sus paquetes y sus pasos se guardan
// en UNA operación en la base, el mismo camino que usa la web.
// ─────────────────────────────────────────────────────────────────────────────

/** Paquete del tarifario: N sesiones por S/ X. */
data class TarifaServicio(val cantidadSesiones: Int, val precioTotal: Double)

/** Especialidad tal como la necesita Servicios (color, ícono, modo heredado, la inicial). */
data class EspecialidadServicio(
    val id: String,
    val nombre: String,
    val color: String? = null,
    val icono: String? = null,
    val rubro: String? = null,
    val estado: String = "Activa",
    /** null = la base no lo dice (se toma como "con sesiones", igual que la web). */
    val usaSesiones: Boolean? = null,
    val createdAt: String? = null,
) {
    val activa: Boolean get() = estado == "Activa"
}

/** Una fila de `procedimientos` con sus paquetes y su especialidad. */
data class ServicioApp(
    val id: String,
    val nombre: String,
    val descripcion: String? = null,
    val categoria: String? = null,
    val categoriaLibre: String? = null,
    val precio: Double = 0.0,
    val precioPaquete: Double? = null,
    /** null = heredar de la especialidad. */
    val modoCobro: String? = null,
    val unidadLabel: String? = null,
    val precioUnitarioSugerido: Double? = null,
    /** Odontología: "1"/"2"/"3" (3 = 3 o más) → precio. Vacío = precio único. */
    val precioPorCaras: Map<String, Double> = emptyMap(),
    val controlesDias: List<Int> = emptyList(),
    val sesionesAuto: Boolean = false,
    val sesionesIntervaloDias: Int? = null,
    val estado: String = "Activo",
    val especialidadId: String? = null,
    val especialidad: EspecialidadServicio? = null,
    val tarifarios: List<TarifaServicio> = emptyList(),
    /** Si está, este servicio define el precio de ese tipo de cita (Consulta/Evaluación). */
    val tipoCita: String? = null,
    val tipoClinico: String? = null,
    val devolucion: String? = null,
    val devolucionProcedimientoId: String? = null,
    val createdAt: String? = null,
) {
    val activo: Boolean get() = estado == "Activo"
    val esEvaluacionPsico: Boolean get() = tipoClinico == TIPO_CLINICO_EVALUACION_PSICO
}

/** Fila de un paso encadenado en el formulario (texto tal cual se escribe). */
data class PasoServicioForm(val pasoId: String = "", val dias: String = "7", val precio: String = "")

/** Presets de la escalera de controles. Gemelo de PROTOCOLOS_SUGERIDOS (lib/escalera-controles.ts). */
val PROTOCOLOS_CONTROLES: List<Pair<String, List<Int>>> = listOf(
    "Capilar (injerto)" to listOf(7, 30, 90, 180, 365),
    "Post-operatorio corto" to listOf(7, 30, 90),
    "Control mensual (6 meses)" to listOf(30, 60, 90, 120, 150, 180),
    "Un solo control" to listOf(30),
)

/** "7 días" / "1 mes" / "2 semanas" / "1 año". Gemelo de etiquetaControl (lib/escalera-controles.ts). */
fun etiquetaControl(dias: Int): String {
    if (dias >= 365 && dias % 365 == 0) { val a = dias / 365; return if (a == 1) "1 año" else "$a años" }
    if (dias >= 30 && dias % 30 == 0) { val m = dias / 30; return if (m == 1) "1 mes" else "$m meses" }
    if (dias % 7 == 0 && dias < 30) { val s = dias / 7; return if (s == 1) "1 semana" else "$s semanas" }
    return if (dias == 1) "1 día" else "$dias días"
}

/**
 * Modo de cobro EFECTIVO: el propio del servicio o, si no fija uno, el de la
 * especialidad (sin sesiones → pago único; con sesiones o sin dato → sesiones).
 * Gemelo de modoCobroEfectivo (lib/procedimientos.ts).
 */
fun modoCobroEfectivo(modoCobro: String?, usaSesiones: Boolean?): String =
    modoCobro?.takeIf { it.isNotBlank() } ?: if (usaSesiones == false) "simple" else "sesiones"

/** "/ sesión", "/ folículo" o "" (pago único). Gemelo de sufijoPrecio (lib/procedimientos.ts). */
fun sufijoPrecio(s: ServicioApp): String = when (modoCobroEfectivo(s.modoCobro, s.especialidad?.usaSesiones)) {
    "sesiones" -> "/ sesión"
    "unidades" -> "/ ${s.unidadLabel?.trim()?.takeIf { it.isNotEmpty() } ?: "unidad"}"
    else -> ""
}

/** Tramos de precio por caras configurados (1, 2, 3+), en orden y solo los > 0. */
fun tramosCaras(s: ServicioApp): List<Pair<String, Double>> =
    listOf("1", "2", "3").mapNotNull { k -> s.precioPorCaras[k]?.takeIf { it > 0 }?.let { k to it } }

/** "1 cara" / "2 caras" / "3+ caras". */
fun etiquetaCaras(k: String): String = when (k) { "1" -> "1 cara"; "2" -> "2 caras"; else -> "3+ caras" }

/**
 * Filtro de la lista: búsqueda por nombre o descripción; estado "" = solo
 * activos (por defecto), "Inactivo", o "todos"; especialidad null = todas.
 */
fun filtrarServicios(lista: List<ServicioApp>, busqueda: String, filtroEstado: String, filtroEsp: String?): List<ServicioApp> {
    val q = busqueda.trim().lowercase()
    return lista.filter { p ->
        val estadoOk = when (filtroEstado) {
            "todos" -> true
            "" -> p.estado == "Activo"
            else -> p.estado == filtroEstado
        }
        val textoOk = q.isEmpty() || p.nombre.lowercase().contains(q) || (p.descripcion?.lowercase()?.contains(q) == true)
        estadoOk && textoOk && (filtroEsp == null || p.especialidadId == filtroEsp)
    }
}

/**
 * Un grupo de la lista. [clave] = el `especialidad_id` ("" = sin especialidad):
 * es la llave estable de la fila (la especialidad puede no encontrarse, p. ej.
 * una que ya no se ve, y dos grupos no pueden compartir llave en la lista).
 */
data class GrupoServicios(val clave: String, val especialidad: EspecialidadServicio?, val servicios: List<ServicioApp>)

/**
 * Agrupa por especialidad en el orden en que aparecen; los "sin especialidad"
 * van AL FINAL (como la web: son los menos organizados, su lugar es el cierre).
 */
fun agruparPorEspecialidad(
    filtrados: List<ServicioApp>,
    especialidades: List<EspecialidadServicio>,
): List<GrupoServicios> {
    val grupos = LinkedHashMap<String, MutableList<ServicioApp>>()
    filtrados.forEach { p -> grupos.getOrPut(p.especialidadId ?: "") { mutableListOf() }.add(p) }
    val conEsp = grupos.filterKeys { it.isNotEmpty() }
        .map { (k, v) -> GrupoServicios(k, especialidades.find { it.id == k } ?: v.first().especialidad, v.toList()) }
    val sin = grupos[""]?.let { listOf(GrupoServicios("", null, it.toList())) }.orEmpty()
    return conEsp + sin
}

/** La especialidad INICIAL de la clínica: la primera que creó (la principal). */
fun especialidadInicial(activas: List<EspecialidadServicio>): EspecialidadServicio? =
    activas.minByOrNull { it.createdAt.orEmpty() }

/** ¿Es psicología? "psicolog", no "psic": Psicomotricidad no es psicología. Gemelo del form web. */
fun esEspecialidadPsico(e: EspecialidadServicio): Boolean =
    e.rubro == "psicologia" || (e.rubro.isNullOrBlank() && e.nombre.contains("psicolog", ignoreCase = true))

/** Número para un campo de texto: "80" o "80.5" (sin ".0"). */
fun numeroEnCampo(n: Double?): String = when {
    n == null -> ""
    n % 1.0 == 0.0 -> n.toLong().toString()
    else -> n.toString()
}

/** Estado del formulario de un servicio (todo texto, como se escribe). */
data class FormServicio(
    val nombre: String = "",
    val descripcion: String = "",
    val especialidadId: String = "",
    val categoriaLibre: String = "",
    val precio: String = "",
    val tarifarios: List<Pair<String, String>> = emptyList(),   // (nº sesiones, precio total)
    /** "" = heredar de la especialidad. */
    val modoCobro: String = "",
    val unidadLabel: String = "",
    val precioUnitario: String = "",
    val precioCaras: Map<String, String> = mapOf("1" to "", "2" to "", "3" to ""),
    val controlesDias: List<Int> = emptyList(),
    val pasos: List<PasoServicioForm> = emptyList(),
    val serieAuto: Boolean = false,
    val serieIntervalo: String = "15",
    val estado: String = "Activo",
    val evalPsico: Boolean = false,
    val devolucion: String = "incluida",
    val devolucionProcId: String = "",
    val citasEstimadas: String = "4",
) {
    val listoParaGuardar: Boolean get() = nombre.isNotBlank() && precio.isNotBlank()

    companion object {
        /** Form vacío (nuevo) o cargado desde un servicio. Los pasos llegan aparte. Gemelo del useEffect del form web. */
        fun desde(s: ServicioApp?, especialidadPorDefecto: String?): FormServicio = if (s == null) {
            FormServicio(especialidadId = especialidadPorDefecto.orEmpty())
        } else FormServicio(
            nombre = s.nombre,
            descripcion = s.descripcion.orEmpty(),
            especialidadId = s.especialidadId.orEmpty(),
            categoriaLibre = s.categoriaLibre.orEmpty(),
            precio = numeroEnCampo(s.precio),
            tarifarios = s.tarifarios.map { it.cantidadSesiones.toString() to numeroEnCampo(it.precioTotal) },
            modoCobro = s.modoCobro.orEmpty(),
            unidadLabel = s.unidadLabel.orEmpty(),
            precioUnitario = numeroEnCampo(s.precioUnitarioSugerido),
            precioCaras = listOf("1", "2", "3").associateWith { numeroEnCampo(s.precioPorCaras[it]) },
            controlesDias = s.controlesDias,
            serieAuto = s.sesionesAuto,
            serieIntervalo = s.sesionesIntervaloDias?.toString() ?: "15",
            estado = s.estado,
            evalPsico = s.esEvaluacionPsico,
            devolucion = if (s.devolucion == "adicional") "adicional" else "incluida",
            devolucionProcId = s.devolucionProcedimientoId.orEmpty(),
            citasEstimadas = s.tarifarios.firstOrNull()?.cantidadSesiones?.toString() ?: "4",
        )
    }
}

/** Modo heredado de la especialidad elegida: con sesiones → 'sesiones'; sin sesiones o "General" → 'simple'. */
fun modoHeredado(especialidadId: String, activas: List<EspecialidadServicio>): String {
    val esp = activas.find { it.id == especialidadId } ?: return "simple"
    return if (esp.usaSesiones != false) "sesiones" else "simple"
}

/** Modo efectivo del FORMULARIO (lo que decide qué secciones de precio se ven). */
fun modoEfectivoForm(f: FormServicio, activas: List<EspecialidadServicio>): String =
    f.modoCobro.ifBlank { modoHeredado(f.especialidadId, activas) }

private fun aEntero(t: String): Int? = t.trim().toDoubleOrNull()?.toInt()
private fun aDecimal(t: String): Double? = t.trim().replace(',', '.').toDoubleOrNull()
/** Escrito pero no se entiende ("." o "1.2.3"): no se guarda en silencio como 0. */
private fun malEscrito(t: String): Boolean = t.isNotBlank() && aDecimal(t) == null

/**
 * Qué impide guardar el formulario (null = nada). Un precio que no se entiende
 * NO se guarda como S/ 0 ni se descarta el paquete en silencio: se avisa.
 */
fun problemaFormServicio(f: FormServicio, activas: List<EspecialidadServicio>, mostrarTipoClinico: Boolean, esDental: Boolean): String? {
    val modo = modoEfectivoForm(f, activas)
    val evalActiva = mostrarTipoClinico && f.evalPsico
    return when {
        f.precio.isNotBlank() && aDecimal(f.precio).let { it == null || it < 0 } -> "El precio no es válido"
        modo == "sesiones" && !evalActiva && f.tarifarios.any { (n, p) ->
            (n.isNotBlank() || p.isNotBlank()) &&
                ((aEntero(n) ?: 0) < 1 || aDecimal(p).let { it == null || it < 0 || it > 999999.99 })
        } -> "Revisa los paquetes: cada uno necesita el número de sesiones y el precio total"
        modo == "unidades" && malEscrito(f.precioUnitario) -> "El precio por unidad no es válido"
        esDental && modo != "sesiones" && f.precioCaras.values.any(::malEscrito) -> "Revisa el precio según caras"
        f.pasos.any { it.pasoId.isNotBlank() && malEscrito(it.precio) } -> "Revisa el precio de los pasos encadenados"
        else -> null
    }
}

/**
 * Cuerpo de /api/staff/servicio a partir del formulario. Port 1:1 de
 * `handleSubmit` de components/procedimientos/ProcedimientoForm.tsx (si cambia
 * uno, cambia el otro). El servidor vuelve a validar todo.
 *
 * @param tipoClinicoInicial el `tipo_clinico` que tenía el servicio al abrirlo (para desmarcarlo).
 * @param mostrarTipoClinico la clínica ve la opción de evaluación psicológica.
 * @param esDental el servicio es de una especialidad dental (precio por caras).
 * @param incluirPasos false si los pasos del servicio no se pudieron leer: la
 *   clave `pasos` no viaja (ausente = no se tocan). Mandarla vacía los borraría.
 */
fun cuerpoGuardarServicio(
    f: FormServicio,
    activas: List<EspecialidadServicio>,
    idEditado: String?,
    tipoClinicoInicial: String?,
    mostrarTipoClinico: Boolean,
    esDental: Boolean,
    incluirPasos: Boolean = true,
): JsonObject {
    val modo = modoEfectivoForm(f, activas)
    // null si no se entiende: viaja como null y el servidor lo rechaza ("El precio no es válido").
    val precio = aDecimal(f.precio)
    val evalActiva = mostrarTipoClinico && f.evalPsico
    val tarifas: List<Pair<Int, Double>> = if (evalActiva) {
        // Evaluación psicológica: un solo paquete = N citas estimadas por el precio de la evaluación.
        if (precio == null) emptyList() else listOf(maxOf(1, aEntero(f.citasEstimadas) ?: 1) to precio)
    } else {
        f.tarifarios.filter { (n, p) -> n.isNotBlank() && p.isNotBlank() }
            .mapNotNull { (n, p) -> val ni = aEntero(n); val pd = aDecimal(p); if (ni == null || pd == null) null else ni to pd }
    }
    val categoria = f.categoriaLibre.trim()
    val especialidad = f.especialidadId.ifBlank { especialidadInicial(activas)?.id.orEmpty() }
    val caras = if (esDental) listOf("1", "2", "3").mapNotNull { k -> aDecimal(f.precioCaras[k].orEmpty())?.takeIf { it > 0 }?.let { k to it } } else emptyList()

    val servicio = buildJsonObject {
        put("nombre", f.nombre.trim())
        put("descripcion", f.descripcion.trim().ifEmpty { null })
        put("categoria", categoria.ifEmpty { "General" })
        put("categoria_libre", categoria.ifEmpty { null })
        if (especialidad.isNotEmpty()) put("especialidad_id", especialidad)
        put("precio", precio)
        put("modo_cobro", f.modoCobro.ifBlank { null })
        put("unidad_label", if (modo == "unidades") f.unidadLabel.trim().ifEmpty { null } else null)
        put("precio_unitario_sugerido", if (modo == "unidades") aDecimal(f.precioUnitario) else null)
        if (caras.isEmpty()) put("precio_por_caras", JsonNull)
        else put("precio_por_caras", buildJsonObject { caras.forEach { (k, v) -> put(k, v) } })
        if (f.controlesDias.isEmpty()) put("controles_dias", JsonNull)
        else put("controles_dias", buildJsonArray { f.controlesDias.sorted().forEach { add(JsonPrimitive(it)) } })
        put("sesiones_auto", modo == "sesiones" && f.serieAuto)
        put("sesiones_intervalo_dias", if (modo == "sesiones" && f.serieAuto) (aEntero(f.serieIntervalo)?.takeIf { it > 0 } ?: 15) else null)
        put("estado", f.estado)
        // Las columnas de evaluación solo viajan al marcarla o al DESMARCAR una que lo era.
        if (evalActiva) {
            put("tipo_clinico", TIPO_CLINICO_EVALUACION_PSICO)
            put("devolucion", f.devolucion)
            put("devolucion_procedimiento_id", if (f.devolucion == "adicional") f.devolucionProcId.ifBlank { null } else null)
        } else if (!tipoClinicoInicial.isNullOrBlank()) {
            put("tipo_clinico", JsonNull)
            put("devolucion", JsonNull)
            put("devolucion_procedimiento_id", JsonNull)
        }
    }
    return buildJsonObject {
        if (idEditado != null) put("id", idEditado)
        put("servicio", servicio)
        // Los paquetes solo aplican al modo sesiones.
        put("tarifarios", buildJsonArray {
            if (modo == "sesiones") tarifas.forEach { (n, p) -> add(buildJsonObject { put("cantidad_sesiones", n); put("precio_total", p) }) }
        })
        if (incluirPasos) put("pasos", buildJsonArray {
            f.pasos.filter { it.pasoId.isNotBlank() }.forEachIndexed { i, p ->
                add(buildJsonObject {
                    put("paso_procedimiento_id", p.pasoId)
                    put("dias_habiles", aEntero(p.dias)?.takeIf { it > 0 } ?: 7)   // vacío o 0 → 7, como la web
                    put("precio", aDecimal(p.precio))
                    put("orden", i)
                })
            }
        })
    }
}

// ── Parseo de filas ──────────────────────────────────────────────────────────

private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
private fun JsonObject.dbl(k: String): Double? = str(k)?.toDoubleOrNull()
private fun JsonObject.bool(k: String): Boolean? = when (str(k)) { "true" -> true; "false" -> false; else -> null }

internal fun parsearEspecialidadServicio(o: JsonObject?): EspecialidadServicio? {
    if (o == null) return null
    val id = o.str("id") ?: return null
    return EspecialidadServicio(
        id = id, nombre = o.str("nombre") ?: "Especialidad", color = o.str("color"), icono = o.str("icono"),
        rubro = o.str("rubro"), estado = o.str("estado") ?: "Activa", usaSesiones = o.bool("usa_sesiones"),
        createdAt = o.str("created_at"),
    )
}

internal fun parsearServicio(o: JsonObject): ServicioApp? {
    val id = o.str("id") ?: return null
    val caras = (o["precio_por_caras"] as? JsonObject)?.mapNotNull { (k, v) ->
        (v as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.let { k to it }
    }?.toMap().orEmpty()
    return ServicioApp(
        id = id,
        nombre = o.str("nombre") ?: "Servicio",
        descripcion = o.str("descripcion"),
        categoria = o.str("categoria"),
        categoriaLibre = o.str("categoria_libre"),
        precio = o.dbl("precio") ?: 0.0,
        precioPaquete = o.dbl("precio_paquete"),
        modoCobro = o.str("modo_cobro"),
        unidadLabel = o.str("unidad_label"),
        precioUnitarioSugerido = o.dbl("precio_unitario_sugerido"),
        precioPorCaras = caras,
        controlesDias = (o["controles_dias"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }.orEmpty(),
        sesionesAuto = o.bool("sesiones_auto") ?: false,
        sesionesIntervaloDias = o.str("sesiones_intervalo_dias")?.toIntOrNull(),
        estado = o.str("estado") ?: "Activo",
        especialidadId = o.str("especialidad_id"),
        especialidad = parsearEspecialidadServicio(o["especialidad"] as? JsonObject),
        tarifarios = (o["tarifarios"] as? JsonArray)?.mapNotNull { t ->
            val to = t as? JsonObject ?: return@mapNotNull null
            val n = to.str("cantidad_sesiones")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            TarifaServicio(n, to.dbl("precio_total") ?: 0.0)
        }?.sortedBy { it.cantidadSesiones }.orEmpty(),
        tipoCita = o.str("tipo_cita"),
        tipoClinico = o.str("tipo_clinico"),
        devolucion = o.str("devolucion"),
        devolucionProcedimientoId = o.str("devolucion_procedimiento_id"),
        createdAt = o.str("created_at"),
    )
}

internal fun parsearPasos(filas: List<JsonObject>): List<PasoServicioForm> = filas.mapNotNull { f ->
    val paso = f.str("paso_procedimiento_id") ?: return@mapNotNull null
    val precio = f.dbl("precio")
    PasoServicioForm(
        pasoId = paso,
        dias = f.str("dias_habiles")?.toIntOrNull()?.toString() ?: "7",
        precio = if (precio != null && precio > 0) numeroEnCampo(precio) else "",
    )
}

/** "3 servicio(s) nuevo(s), 2 ya existían; 1 consentimiento(s) asociado(s)." (como la web). */
internal fun resumenTipicos(cuerpo: JsonObject?): String {
    fun n(k: String) = (cuerpo?.get(k) as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    return "${n("creados")} servicio(s) nuevo(s), ${n("existentes")} ya existían; ${n("vinculados")} consentimiento(s) asociado(s)."
}

object ServiciosRepo {

    private val http = crearHttpClient()

    private const val COLUMNAS =
        "*, tarifarios:tarifario_paquetes(*), especialidad:especialidades(id, nombre, color, icono, usa_sesiones, rubro, estado, created_at)"

    private suspend fun token(): String? = Supabase.client.auth.currentSessionOrNull()?.accessToken

    private val sinSesion = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Tu sesión expiró. Vuelve a entrar.", "NO_AUTENTICADO", 401),
    )
    private val sinRed = ResultadoEscritura(
        registrada = false, rechazo = RechazoServidor("Sin conexión. Revisa tu internet.", "SIN_RED"),
    )

    /** Todo el catálogo (activos e inactivos), lo más nuevo primero, como la web. */
    suspend fun listar(): List<ServicioApp> =
        Supabase.client.postgrest["procedimientos"]
            .select(Columns.raw(COLUMNAS)) { order("created_at", Order.DESCENDING) }
            .decodeList<JsonObject>()
            .mapNotNull(::parsearServicio)

    suspend fun especialidades(): List<EspecialidadServicio> =
        Supabase.client.postgrest["especialidades"]
            .select(Columns.list("id, nombre, color, icono, rubro, estado, usa_sesiones, created_at")) {
                order("nombre", Order.ASCENDING)
            }
            .decodeList<JsonObject>()
            .mapNotNull { parsearEspecialidadServicio(it) }

    /** Pasos encadenados del servicio en edición (consulta plana, como el form web). */
    suspend fun pasosDe(id: String): List<PasoServicioForm> =
        parsearPasos(
            Supabase.client.postgrest["procedimiento_pasos"]
                .select(Columns.list("paso_procedimiento_id, dias_habiles, precio")) {
                    filter { eq("procedimiento_id", id) }
                    order("orden", Order.ASCENDING)
                }
                .decodeList<JsonObject>()
        )

    private suspend fun enviar(llamada: suspend (String) -> HttpResponse): ResultadoEscritura {
        val tk = token() ?: return sinSesion
        return try {
            val resp = llamada(tk)
            AtencionRepo.resultadoDeRespuesta(resp.status.value, runCatching { resp.bodyAsText() }.getOrNull())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            sinRed
        }
    }

    /** Crear (sin `id` en el cuerpo) o editar (con `id`): ver [cuerpoGuardarServicio]. */
    suspend fun guardar(cuerpo: JsonObject): ResultadoEscritura = enviar { tk ->
        val url = "${Supabase.SITE_URL}/api/staff/servicio"
        val conId = cuerpo["id"] != null
        if (conId) http.patch(url) {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        } else http.post(url) {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody(cuerpo.toString())
        }
    }

    /** Activar / desactivar (estado explícito: reintentar no lo da vuelta). */
    suspend fun cambiarEstado(id: String, estado: String): ResultadoEscritura = enviar { tk ->
        http.post("${Supabase.SITE_URL}/api/staff/servicio/estado") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("id", id); put("estado", estado) }.toString())
        }
    }

    /** "🩹 Cargar procedimientos típicos" (flujo médico). Idempotente en el servidor. */
    suspend fun cargarTipicos(): ResultadoEscritura = enviar { tk ->
        http.post("${Supabase.SITE_URL}/api/staff/servicio/tipicos") {
            header("Authorization", "Bearer $tk"); contentType(ContentType.Application.Json); setBody("{}")
        }
    }
}
