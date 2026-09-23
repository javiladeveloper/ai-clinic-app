package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
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

    /**
     * El catálogo de hallazgos de la clínica (Caries, Ausente, Sarro…).
     *
     * Trae también los INACTIVOS: un hallazgo que la clínica desactivó hoy
     * sigue existiendo en bocas marcadas antes, y sin su nombre y color el
     * diagrama se pintaría con huecos.
     */
    suspend fun catalogo(): List<HallazgoDental> = try {
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
    } catch (_: Exception) { emptyList() }

    /** Lo marcado en la boca de un paciente. */
    suspend fun hallazgos(pacienteId: String): List<DienteHallazgo> = try {
        Supabase.client.postgrest["dientes_hallazgos"]
            .select(Columns.list("id, paciente_id, diente, hallazgo_id, superficies, estado, cita_id, tratamiento_id, notas, fecha")) {
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
                )
            }
    } catch (_: Exception) { emptyList() }

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
    } catch (_: Exception) { false }

    /**
     * Pasa un hallazgo a Realizado, o lo devuelve a Pendiente.
     *
     * Revertir importa: el odontólogo marca "listo" por error y sin vuelta
     * atrás tendría que borrarlo y volver a marcarlo, perdiendo la fecha.
     */
    suspend fun cambiarEstado(id: String, estado: String): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"]
            .update({ set("estado", estado) }) { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }

    /** Quita un hallazgo (se marcó el diente equivocado). */
    suspend fun borrar(id: String): Boolean = try {
        Supabase.client.postgrest["dientes_hallazgos"].delete { filter { eq("id", id) } }
        true
    } catch (_: Exception) { false }

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
    } catch (_: Exception) { false }

    /**
     * Los servicios de ODONTOLOGÍA de la clínica, para el presupuesto.
     *
     * En una clínica mixta (medicina + odontología, o fisio + odontología) el
     * presupuesto dental no debe ofrecer servicios de otra rama: se filtra por
     * las especialidades cuyo rubro guardado es odontología. Si la clínica no
     * tiene el rubro cargado (clínicas viejas), se ofrecen todos, que es lo que
     * hacía antes.
     */
    suspend fun serviciosDentales(): List<ProcedimientoRef> {
        val idsOdonto = try {
            Supabase.client.postgrest["especialidades"]
                .select(Columns.list("id")) { filter { eq("rubro", "odontologia") } }
                .decodeList<JsonObject>()
                .mapNotNull { it.str("id") }
        } catch (_: Exception) { emptyList() }
        val todos = procedimientos()
        if (idsOdonto.isEmpty()) return todos
        return todos.filter { it.especialidadId == null || it.especialidadId in idsOdonto }
    }
}
