package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pe.saniape.app.data.Supabase

/**
 * Evaluación fisioterapéutica (M5/M6/M8): tablas `evaluaciones_fisio` y
 * `objetivos_tratamiento`. Gemelo de `lib/evaluacion-fisio-db.ts`.
 *
 * Lee y escribe DIRECTO con la RLS del staff (no toca dinero ni contadores:
 * no pasa por /api/staff). `clinica_id` NUNCA se manda: su DEFAULT es
 * get_clinica_id() y forzarlo rompe la RLS.
 */
object EvaluacionFisioRepo {

    private const val COLS_EVAL =
        "id, paciente_id, tratamiento_id, cita_id, terapeuta_id, tipo, fecha, datos, created_at, terapeuta:terapeutas(id, nombre)"
    private const val COLS_OBJ =
        "id, paciente_id, tratamiento_id, evaluacion_id, texto, unidad, valor_inicial, meta, logrado, estado, fecha_estado, orden, created_at"

    private fun JsonObject.str(k: String): String? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.dbl(k: String): Double? =
        (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()

    data class DatosEvaluacion(val evaluaciones: List<EvaluacionFisio>, val objetivos: List<ObjetivoTratamiento>)

    /** Dos consultas chicas en PARALELO por el índice de paciente. null = falló la red (no "vacío"). */
    suspend fun cargar(pacienteId: String): DatosEvaluacion? = try {
        coroutineScope {
            val ev = async {
                Supabase.client.postgrest["evaluaciones_fisio"].select(Columns.raw(COLS_EVAL)) {
                    filter { eq("paciente_id", pacienteId) }
                    order("fecha", Order.ASCENDING)
                    order("created_at", Order.ASCENDING)
                }.decodeList<JsonObject>()
            }
            val ob = async {
                Supabase.client.postgrest["objetivos_tratamiento"].select(Columns.raw(COLS_OBJ)) {
                    filter { eq("paciente_id", pacienteId) }
                    order("orden", Order.ASCENDING)
                    order("created_at", Order.ASCENDING)
                }.decodeList<JsonObject>()
            }
            DatosEvaluacion(
                evaluaciones = ev.await().mapNotNull { o -> evaluacionDe(o) },
                objetivos = ob.await().mapNotNull { o -> objetivoDe(o) },
            )
        }
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private fun evaluacionDe(o: JsonObject): EvaluacionFisio? {
        val id = o.str("id") ?: return null
        val ter = when (val t = o["terapeuta"]) {
            is JsonObject -> t
            is kotlinx.serialization.json.JsonArray -> t.firstOrNull() as? JsonObject
            else -> null
        }
        return EvaluacionFisio(
            id = id,
            pacienteId = o.str("paciente_id") ?: "",
            tratamientoId = o.str("tratamiento_id"),
            citaId = o.str("cita_id"),
            terapeutaId = o.str("terapeuta_id"),
            tipo = o.str("tipo") ?: "inicial",
            fecha = (o.str("fecha") ?: "").take(10),
            datos = datosDeJson(o["datos"]),
            createdAt = o.str("created_at"),
            terapeutaNombre = ter?.str("nombre"),
        )
    }

    private fun objetivoDe(o: JsonObject): ObjetivoTratamiento? {
        val id = o.str("id") ?: return null
        return ObjetivoTratamiento(
            id = id,
            pacienteId = o.str("paciente_id") ?: "",
            tratamientoId = o.str("tratamiento_id"),
            evaluacionId = o.str("evaluacion_id"),
            texto = o.str("texto") ?: "",
            unidad = o.str("unidad"),
            valorInicial = o.dbl("valor_inicial"),
            meta = o.dbl("meta"),
            logrado = o.dbl("logrado"),
            estado = o.str("estado") ?: "en_curso",
            fechaEstado = o.str("fecha_estado"),
            orden = o.dbl("orden")?.toInt() ?: 0,
            createdAt = o.str("created_at"),
        )
    }

    /**
     * Guarda una evaluación (y los objetivos puestos en ella). Con [citaId] hace
     * upsert por la cita: completar dos veces no duplica. Devuelve el id, o null si
     * no había nada que guardar. Lanza si falla (quien llama decide cómo avisar).
     */
    suspend fun guardar(
        pacienteId: String,
        tipo: String,
        datos: EvaluacionFisioDatos,
        objetivos: List<ObjetivoBorrador> = emptyList(),
        tratamientoId: String? = null,
        citaId: String? = null,
        terapeutaId: String? = null,
        fecha: String? = null,
    ): String? {
        val objs = objetivos.filter { it.texto.isNotBlank() }
        if (!tieneDatos(datos) && objs.isEmpty()) return null
        val fila = buildJsonObject {
            put("paciente_id", pacienteId)
            put("tratamiento_id", tratamientoId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("cita_id", citaId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("terapeuta_id", terapeutaId?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("tipo", tipo)
            put("fecha", (fecha?.takeIf { it.isNotBlank() } ?: hoyClinicaIso()).take(10))
            put("datos", datosAJson(datos))
        }
        val tabla = Supabase.client.postgrest["evaluaciones_fisio"]
        val res = if (citaId != null) {
            tabla.upsert(fila) {
                onConflict = "cita_id"
                select(Columns.list("id"))
            }
        } else {
            tabla.insert(fila) { select(Columns.list("id")) }
        }
        val id = res.decodeSingle<JsonObject>().str("id") ?: error("sin id")
        if (objs.isNotEmpty()) {
            // Idempotente con la evaluación de una cita (upsert): si se reintenta, no se repiten.
            val existentes = if (citaId != null) {
                Supabase.client.postgrest["objetivos_tratamiento"].select(Columns.list("texto")) {
                    filter { eq("evaluacion_id", id) }
                }.decodeList<JsonObject>().mapNotNull { it.str("texto")?.lowercase() }.toSet()
            } else emptySet()
            val nuevos = objs.filter { it.texto.trim().lowercase() !in existentes }.mapIndexed { i, o ->
                buildJsonObject {
                    put("paciente_id", pacienteId)
                    put("tratamiento_id", tratamientoId?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("evaluacion_id", id)
                    put("texto", o.texto.trim())
                    put("unidad", o.unidad?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("valor_inicial", numeroAJson(o.valorInicial))
                    put("meta", numeroAJson(o.meta))
                    put("orden", i)
                }
            }
            if (nuevos.isNotEmpty()) Supabase.client.postgrest["objetivos_tratamiento"].insert(nuevos)
        }
        return id
    }

    /**
     * Al completar una Evaluación de fisio: guarda lo llenado en "Evaluación
     * estructurada". NUNCA bloquea el completar. null = no había nada (o era de otra
     * cita); true = guardado; false = falló (quien llama avisa: la cita ya se completó).
     * Tipo 'inicial' y la fecha de la ATENCIÓN (se puede completar tarde).
     */
    suspend fun guardarDeCita(
        borrador: BorradorEvaluacionFisio?,
        citaId: String,
        pacienteId: String?,
        tratamientoId: String?,
        fechaCita: String?,
        terapeutaId: String?,
    ): Boolean? {
        if (borrador == null || borrador.citaId != citaId || pacienteId == null) return null
        if (!tieneDatos(borrador.datos) && borrador.objetivos.none { it.texto.isNotBlank() }) return null
        return try {
            guardar(
                pacienteId = pacienteId, tipo = "inicial", datos = borrador.datos, objetivos = borrador.objetivos,
                tratamientoId = tratamientoId, citaId = citaId, terapeutaId = terapeutaId, fecha = fechaCita,
            )
            true
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

    suspend fun actualizar(
        id: String, datos: EvaluacionFisioDatos, tipo: String, fecha: String, terapeutaId: String?, tratamientoId: String?,
    ): Boolean = try {
        Supabase.client.postgrest["evaluaciones_fisio"].update(buildJsonObject {
            put("datos", datosAJson(datos))
            put("tipo", tipo)
            put("fecha", fecha.take(10))
            put("terapeuta_id", terapeutaId?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("tratamiento_id", tratamientoId?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        }) { filter { eq("id", id) } }
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    suspend fun borrar(id: String): Boolean = try {
        Supabase.client.postgrest["evaluaciones_fisio"].delete { filter { eq("id", id) } }
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    suspend fun crearObjetivo(o: ObjetivoBorrador, pacienteId: String, tratamientoId: String?, orden: Int): Boolean = try {
        Supabase.client.postgrest["objetivos_tratamiento"].insert(buildJsonObject {
            put("paciente_id", pacienteId)
            put("tratamiento_id", tratamientoId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("texto", o.texto.trim())
            put("unidad", o.unidad?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
            put("valor_inicial", numeroAJson(o.valorInicial))
            put("meta", numeroAJson(o.meta))
            put("orden", orden)
        })
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    /** Cambia el estado (con su fecha, en Perú) y/o el valor logrado. */
    suspend fun actualizarObjetivo(id: String, estado: String? = null, logrado: Double? = null, tocarLogrado: Boolean = false): Boolean = try {
        Supabase.client.postgrest["objetivos_tratamiento"].update(buildJsonObject {
            if (estado != null) {
                put("estado", estado)
                put("fecha_estado", if (estado == "en_curso") JsonNull else JsonPrimitive(hoyClinicaIso()))
            }
            if (tocarLogrado) put("logrado", numeroAJson(logrado))
        }) { filter { eq("id", id) } }
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    suspend fun borrarObjetivo(id: String): Boolean = try {
        Supabase.client.postgrest["objetivos_tratamiento"].delete { filter { eq("id", id) } }
        true
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
}

/** Lo que se llenó en el bloque "Evaluación estructurada" (solo se guarda si es de ESA cita). */
data class BorradorEvaluacionFisio(
    val citaId: String?,
    val datos: EvaluacionFisioDatos,
    val objetivos: List<ObjetivoBorrador>,
)
