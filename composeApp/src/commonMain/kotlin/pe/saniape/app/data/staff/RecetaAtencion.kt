package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

// ─────────────────────────────────────────────────────────────────────────────
// 📝 RECETA / INDICACIONES TRAS UNA ATENCIÓN
//
// "Después de una sesión se puede dar una receta" (2026-10-09). Al completar una
// sesión o una cita (agenda, ficha, Sesiones) se ofrece "📝 Dar receta /
// indicaciones", que abre la MISMA emisión nativa de la ficha (DialogoReceta →
// POST /api/staff/receta/emitir) prellenada y VINCULADA: tratamiento, cita y
// sesión de esa atención, el profesional que atendió y el diagnóstico.
//
// Nada de esto toca el camino de completar: la oferta sale DESPUÉS del éxito
// (completar sigue siendo un viaje al servidor) y lo que falta para prellenar
// (sesión de la cita, diagnóstico) se lee recién al tocar el botón.
//
// Backend: el de producción ya acepta `sesionId`/`citaId`/`tratamientoId` en
// /api/staff/receta/emitir (columnas `recetas.sesion_id/cita_id/tratamiento_id`,
// validadas por trigger contra el paciente). No hace falta nada nuevo.
// ─────────────────────────────────────────────────────────────────────────────

/** Con qué se abre la receta de una atención (el `PrefillReceta` de la web + la sesión). */
data class PrefillRecetaAtencion(
    val pacienteId: String,
    val pacienteNombre: String? = null,
    /** La cita atendida (null = sesión creada sin agenda, o tratamiento entero). */
    val citaId: String? = null,
    /** La sesión atendida. Desde la agenda puede no saberse: se lee de la cita al abrir. */
    val sesionId: String? = null,
    val tratamientoId: String? = null,
    /** Quien atendió: viene elegido como prescriptor si puede prescribir. */
    val terapeutaId: String? = null,
    /** Diagnóstico de la atención (o del tratamiento / paciente). null = se lee al abrir. */
    val diagnostico: String? = null,
    /** "Sesión #3" / "Consulta" / "Evaluación": solo para el texto de la oferta. */
    val atencion: String? = null,
    /** Lo anotado como medicación en la consulta (texto libre): referencia para transcribir. */
    val medicacionRef: String? = null,
    /** Código CIE-10 del diagnóstico principal (consulta guiada). */
    val cie10: String? = null,
    /** Indicaciones generales ya escritas en la consulta (plan). */
    val indicaciones: String? = null,
)

/**
 * ¿Esta atención puede llevar receta / indicaciones? Gemela de
 * `pacienteRecibeRecetas` de la web aplicada a UNA atención (no al paciente
 * entero): el módulo de recetas encendido, permiso de 'sesiones' (el mismo que
 * exige /api/staff/receta/emitir) y la especialidad de la atención en el mapa
 * de recetas. Manda la especialidad de la cita, luego la del servicio de su
 * tratamiento; sin ninguna, la clínica "solo receta" o alguna especialidad del
 * profesional en el mapa (como `citaEsMedicaGuiada`).
 *
 * DALU (recetas como indicaciones) tiene `mapaReceta.solo`: toda atención aplica.
 * Una clínica con el módulo apagado (RENOVA) nunca ve nada.
 */
fun recetaAplicaAtencion(
    modulos: ModulosClinicos,
    puedeSesiones: Boolean,
    especialidadCitaId: String? = null,
    especialidadServicioId: String? = null,
    especialidadesProfesional: List<String>? = null,
    /** Sin especialidad conocida: también las de quien mira (`ofreceRecetaEnAtencion` web). */
    especialidadesDeQuienMira: List<String>? = null,
): Boolean {
    if (!modulos.recetas || !puedeSesiones) return false
    val mapa = modulos.mapaReceta
    if (!mapa.activo) return false
    if (mapa.solo) return true
    val esp = listOf(especialidadCitaId, especialidadServicioId).firstOrNull { !it.isNullOrBlank() }
    if (esp != null) return esp in mapa.ids
    return (especialidadesProfesional.orEmpty() + especialidadesDeQuienMira.orEmpty()).any { it in mapa.ids }
}

/**
 * Clínica NO médica que encendió recetas (DALU, fisioterapia): la hoja sale como
 * INDICACIONES (sin Rp. ni medicamentos obligatorios), así que todo el texto
 * habla de "indicaciones". En una clínica médica, "receta / indicaciones".
 */
fun recetaComoIndicaciones(modulos: ModulosClinicos): Boolean = modulos.recetasOptIn

/** El botón: "📝 Dar indicaciones" (no médica) o "📝 Dar receta / indicaciones". */
fun textoDarReceta(modulos: ModulosClinicos): String =
    if (recetaComoIndicaciones(modulos)) "📝 Dar indicaciones" else "📝 Dar receta / indicaciones"

/** El sustantivo para avisos e indicadores: "Indicaciones" o "Receta". */
fun nombreHojaReceta(modulos: ModulosClinicos): String =
    if (recetaComoIndicaciones(modulos)) "Indicaciones" else "Receta"

/** La pregunta de la oferta tras completar ("¿Le dejas indicaciones a Ana?"). */
fun textoOfertaReceta(modulos: ModulosClinicos, p: PrefillRecetaAtencion): String {
    val nombre = p.pacienteNombre?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() }
    val a = nombre?.let { " a $it" } ?: ""
    return if (recetaComoIndicaciones(modulos)) "¿Le dejas indicaciones$a?"
    else "¿Le das una receta o indicaciones$a?"
}

/** "✓ Sesión #3 completada" (o solo "✓ Atención completada"). */
fun textoAtencionCompletada(p: PrefillRecetaAtencion): String =
    "✓ ${p.atencion?.takeIf { it.isNotBlank() } ?: "Atención"} completada"

/**
 * El prellenado de una cita de la AGENDA recién completada (o ya completada, desde
 * su tarjeta). [terapeutaId] = quien atendió si se eligió al completar; si no, el
 * de la cita. [diagnostico] = el escrito al completar (evaluación); null = se lee
 * del tratamiento al abrir. [nombreTipo] = cómo llama la clínica a ese tipo.
 * La sesión va si la cita ya la tenía; si nace al completar, se lee de la cita
 * al ABRIR la receta, nunca en el camino de completar.
 */
fun prefillRecetaDeCita(
    cita: CitaStaff,
    terapeutaId: String? = null,
    diagnostico: String? = null,
    nombreTipo: String? = null,
): PrefillRecetaAtencion? {
    val pac = cita.pacienteId?.takeIf { it.isNotBlank() } ?: return null
    val atencion = when {
        cita.tipo == "Sesión" && (cita.numeroSesion ?: 0) > 0 -> "Sesión #${cita.numeroSesion}"
        else -> nombreTipo?.takeIf { it.isNotBlank() } ?: cita.tipo
    }
    return PrefillRecetaAtencion(
        pacienteId = pac,
        pacienteNombre = cita.pacienteNombre,
        citaId = cita.id,
        // Si la cita ya tenía su sesión, va; si nace al completar, se lee al abrir.
        sesionId = cita.sesionId,
        tratamientoId = cita.tratamientoId,
        terapeutaId = terapeutaId?.takeIf { it.isNotBlank() } ?: cita.terapeutaId,
        diagnostico = diagnostico?.trim()?.takeIf { it.isNotEmpty() },
        atencion = atencion,
    )
}

/**
 * El prellenado de una SESIÓN de la ficha o de la lista de Sesiones: su
 * tratamiento, su cita (si se agendó), quien la atendió (o el del tratamiento) y
 * el diagnóstico del tratamiento (o del paciente).
 */
fun prefillRecetaDeSesion(
    pacienteId: String,
    pacienteNombre: String?,
    sesionId: String,
    numero: Int?,
    tratamientoId: String?,
    citaId: String? = null,
    terapeutaSesion: String? = null,
    terapeutaTratamiento: String? = null,
    diagnostico: String? = null,
): PrefillRecetaAtencion = PrefillRecetaAtencion(
    pacienteId = pacienteId,
    pacienteNombre = pacienteNombre,
    citaId = citaId?.takeIf { it.isNotBlank() },
    sesionId = sesionId,
    tratamientoId = tratamientoId,
    terapeutaId = terapeutaSesion?.takeIf { it.isNotBlank() } ?: terapeutaTratamiento,
    diagnostico = diagnostico?.trim()?.takeIf { it.isNotEmpty() },
    atencion = numero?.takeIf { it > 0 }?.let { "Sesión #$it" } ?: "Sesión",
)

/**
 * El prellenado del TRATAMIENTO entero (la consulta registrada, el servicio
 * único, el "📝" de su menú; gemelo del botón "📝 Receta" de la ficha web): la
 * cita de origen solo en una consulta (como la web), y la medicación anotada
 * como referencia para transcribir.
 */
fun prefillRecetaDeTratamiento(
    pacienteId: String,
    pacienteNombre: String?,
    tratamientoId: String,
    esConsulta: Boolean,
    citaOrigenId: String?,
    terapeutaId: String?,
    diagnostico: String?,
    atencion: String?,
    medicacion: String?,
): PrefillRecetaAtencion = PrefillRecetaAtencion(
    pacienteId = pacienteId,
    pacienteNombre = pacienteNombre,
    citaId = if (esConsulta) citaOrigenId?.takeIf { it.isNotBlank() } else null,
    tratamientoId = tratamientoId,
    terapeutaId = terapeutaId,
    diagnostico = diagnostico?.trim()?.takeIf { it.isNotEmpty() },
    atencion = atencion,
    medicacionRef = medicacion?.trim()?.takeIf { it.isNotEmpty() },
)

/**
 * Tras "✓ Terminar atención" de la consulta guiada: se ofrece la receta SOLO si la
 * consulta la admite (`flags.recetasAplica` del servidor) y no se emitió ninguna
 * vigente en ella (el paso Plan ya tiene su "📝 Emitir receta": esto es el
 * recordatorio, no un segundo camino). null = no ofrecer.
 */
fun prefillRecetaTrasConsulta(
    recetasAplica: Boolean,
    estadosRecetasDeLaCita: List<String>,
    pacienteId: String?,
    pacienteNombre: String?,
    citaId: String,
    tratamientoId: String?,
    terapeutaId: String?,
    diagnostico: String?,
    cie10: String?,
    indicaciones: String?,
): PrefillRecetaAtencion? {
    if (!recetasAplica || pacienteId.isNullOrBlank()) return null
    if (estadosRecetasDeLaCita.any { it != "Anulada" }) return null
    return PrefillRecetaAtencion(
        pacienteId = pacienteId,
        pacienteNombre = pacienteNombre,
        citaId = citaId,
        tratamientoId = tratamientoId,
        terapeutaId = terapeutaId,
        diagnostico = diagnostico?.trim()?.takeIf { it.isNotEmpty() },
        atencion = "Consulta",
        cie10 = cie10?.trim()?.takeIf { it.isNotEmpty() },
        indicaciones = indicaciones?.trim()?.takeIf { it.isNotEmpty() },
    )
}

/** Una receta ya emitida y a qué atención quedó atada (sin las anuladas). */
data class RecetaVinculada(
    val id: String,
    val numero: Int?,
    val citaId: String?,
    val sesionId: String?,
    val tratamientoId: String?,
    /**
     * Lo que REALMENTE se emitió (`esHojaDeIndicaciones` web): sin productos o con
     * un prescriptor que no prescribe = hoja de INDICACIONES.
     */
    val esIndicaciones: Boolean = false,
) {
    val numeroTexto: String get() = formatearNumeroReceta(numero)

    /** "Indicaciones" / "Receta" (para "🖨 Ver …" y el visor). */
    val nombreHoja: String get() = if (esIndicaciones) "Indicaciones" else "Receta"
}

/**
 * La receta de ESTA atención: la atada a su sesión o, si no, a su cita (la de la
 * agenda puede haberse emitido antes de conocer la sesión). La más nueva si hay
 * varias. null = la atención no tiene receta.
 */
fun recetaDeAtencion(lista: List<RecetaVinculada>, citaId: String?, sesionId: String?): RecetaVinculada? {
    if (citaId.isNullOrBlank() && sesionId.isNullOrBlank()) return null
    val porSesion = sesionId?.takeIf { it.isNotBlank() }?.let { s -> lista.filter { it.sesionId == s } }.orEmpty()
    val porCita = citaId?.takeIf { it.isNotBlank() }?.let { c -> lista.filter { it.citaId == c } }.orEmpty()
    return (porSesion.ifEmpty { porCita }).maxByOrNull { it.numero ?: 0 }
}

/** Indicador: "💊 Receta N° 000012" o "📋 Indicaciones N° 000012" (gemelo de la web). */
fun etiquetaRecetaVinculada(r: RecetaVinculada): String =
    "${if (r.esIndicaciones) "📋 Indicaciones" else "💊 Receta"} ${r.numeroTexto}"

private fun JsonObject.s(k: String): String? =
    (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" && it.isNotBlank() }

/** Fila de `recetas` → RecetaVinculada (tolerante: sin id se descarta). */
internal fun aRecetaVinculada(o: JsonObject): RecetaVinculada? {
    val id = o.s("id") ?: return null
    if (o.s("estado") == "Anulada") return null
    // Liviano: `primer_dci` (items->0->>dci) y `prescribe` (prescriptor->>prescribe) en vez
    // de los jsonb enteros. Se aceptan también las columnas completas (fila de otra lectura).
    val items = o["items"] as? kotlinx.serialization.json.JsonArray
    val sinItems = if ("primer_dci" in o) o.s("primer_dci") == null else items != null && items.isEmpty()
    val prescribe = o.s("prescribe")
        ?: ((o["prescriptor"] as? JsonObject)?.get("prescribe") as? JsonPrimitive)?.content
    return RecetaVinculada(
        id = id,
        numero = o.s("numero")?.toDoubleOrNull()?.toInt(),
        citaId = o.s("cita_id"),
        sesionId = o.s("sesion_id"),
        tratamientoId = o.s("tratamiento_id"),
        // Sin la columna (fila rara) no se adivina: cuenta como receta.
        esIndicaciones = sinItems || prescribe == "false",
    )
}

/**
 * Lecturas livianas para el indicador y el prellenado. Todas toleran fallos
 * (sin red → vacío / null): un indicador que no carga no rompe la pantalla.
 */
object RecetaAtencionRepo {
    /**
     * Solo lo que el indicador usa: sin los jsonb enteros (`items`/`prescriptor`),
     * apenas si hay un primer producto y si el prescriptor prescribe.
     */
    internal const val COLUMNAS =
        "id, numero, estado, cita_id, sesion_id, tratamiento_id, " +
            "primer_dci:items->0->>dci, prescribe:prescriptor->>prescribe"

    /** Tope de pacientes por consulta `in.(…)` (la URL no crece sin límite). */
    private const val MAX_IDS = 80

    /**
     * Recetas vigentes (no anuladas) de estas ATENCIONES: las de [citaIds] o
     * [sesionIds] de estos pacientes (por `paciente_id`, que tiene índice). Sin
     * ids de atenciones no hay nada que buscar. null = no se pudo leer.
     */
    suspend fun vinculadasDe(
        pacienteIds: Collection<String>,
        citaIds: Collection<String> = emptyList(),
        sesionIds: Collection<String> = emptyList(),
    ): List<RecetaVinculada>? {
        val pacs = pacienteIds.filter { it.isNotBlank() }.distinct()
        val citas = citaIds.filter { it.isNotBlank() }.distinct()
        val sesiones = sesionIds.filter { it.isNotBlank() }.distinct()
        if (pacs.isEmpty() || (citas.isEmpty() && sesiones.isEmpty())) return emptyList()
        return runCatching {
            pacs.chunked(MAX_IDS).flatMap { lote ->
                Supabase.client.postgrest["recetas"]
                    .select(Columns.raw(COLUMNAS)) {
                        filter {
                            isIn("paciente_id", lote)
                            neq("estado", "Anulada")
                            or {
                                if (citas.isNotEmpty()) isIn("cita_id", citas)
                                if (sesiones.isNotEmpty()) isIn("sesion_id", sesiones)
                            }
                        }
                        order("numero", Order.DESCENDING)
                        limit(500)
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { aRecetaVinculada(it) }
            }
        }.getOrNull()
    }

    /**
     * Las de UN paciente que quedaron atadas a una cita o una sesión (la ficha:
     * sus sesiones se cargan por tratamiento, así que se filtra por el vínculo y
     * no por ids). Por `paciente_id` (índice). null = no se pudo leer.
     */
    suspend fun atadasDelPaciente(pacienteId: String): List<RecetaVinculada>? = runCatching {
        Supabase.client.postgrest["recetas"]
            .select(Columns.raw(COLUMNAS)) {
                filter {
                    eq("paciente_id", pacienteId)
                    neq("estado", "Anulada")
                    or {
                        filterNot("cita_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, "null")
                        filterNot("sesion_id", io.github.jan.supabase.postgrest.query.filter.FilterOperator.IS, "null")
                    }
                }
                order("numero", Order.DESCENDING)
                limit(300)
            }
            .decodeList<JsonObject>()
            .mapNotNull { aRecetaVinculada(it) }
    }.getOrNull()

    /**
     * Las recetas de estas SESIONES (lista global de Sesiones: solo las
     * completadas visibles, por `sesion_id`). null = no se pudo leer.
     */
    suspend fun deSesiones(sesionIds: Collection<String>): List<RecetaVinculada>? {
        val ids = sesionIds.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            ids.chunked(MAX_IDS).flatMap { lote ->
                Supabase.client.postgrest["recetas"]
                    .select(Columns.raw(COLUMNAS)) {
                        filter {
                            isIn("sesion_id", lote)
                            neq("estado", "Anulada")
                        }
                        order("numero", Order.DESCENDING)
                    }
                    .decodeList<JsonObject>()
                    .mapNotNull { aRecetaVinculada(it) }
            }
        }.getOrNull()
    }

    /**
     * Completa el prellenado al ABRIR la receta (nunca al completar): la sesión de
     * la cita (la de la agenda nace o se vincula al completar) y el diagnóstico del
     * tratamiento o, si no tiene, el del paciente. Dos lecturas en paralelo, solo
     * de lo que falta. Si fallan, la receta se abre igual con lo que había.
     */
    suspend fun completarPrefill(p: PrefillRecetaAtencion): PrefillRecetaAtencion = coroutineScope {
        val sesion = async {
            if (p.sesionId != null || p.citaId == null) p.sesionId
            else AgendaRepo.sesionDeCita(p.citaId)?.first
        }
        val dx = async {
            if (!p.diagnostico.isNullOrBlank()) p.diagnostico else runCatching {
                if (p.tratamientoId != null) {
                    Supabase.client.postgrest["tratamientos"]
                        .select(Columns.raw("diagnostico, paciente:pacientes(diagnostico)")) {
                            filter { eq("id", p.tratamientoId) }; limit(1)
                        }
                        .decodeList<JsonObject>().firstOrNull()?.let { o ->
                            o.s("diagnostico") ?: (o["paciente"] as? JsonObject)?.s("diagnostico")
                        }
                } else {
                    Supabase.client.postgrest["pacientes"]
                        .select(Columns.list("diagnostico")) { filter { eq("id", p.pacienteId) }; limit(1) }
                        .decodeList<JsonObject>().firstOrNull()?.s("diagnostico")
                }
            }.getOrNull()
        }
        p.copy(sesionId = sesion.await(), diagnostico = dx.await())
    }
}
