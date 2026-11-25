package pe.saniape.app.data.staff

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import pe.saniape.app.data.Supabase

/*
 * ⚠ LAS ALERTAS DE PIEL DE UN PACIENTE FUERA DE SU FICHA (24/11/2026). Gemelo de
 * components/estetica/AlertasEsteticaDePaciente.tsx (web): el detalle de la cita
 * no trae sus «Datos de la ficha», así que se leen aquí. Misma regla que la ficha
 * ([alertasEstetica]): los campos propios de la clínica por su nombre y el fototipo
 * de la escala Fitzpatrick aplicada. Tolerante: si algo falla, sin alertas.
 */
object AlertasEsteticaRepo {
    /** Los campos propios activos de la clínica (id, nombre): cambian poco, una vez por sesión. */
    private var campos: List<Pair<String, String>>? = null

    suspend fun dePaciente(pacienteId: String?): List<AlertaEstetica> {
        if (pacienteId.isNullOrBlank()) return emptyList()
        return try {
            coroutineScope {
                val fototipo = async { EscalasCierreRepo.fototipoDelPaciente(pacienteId) }
                val valores = Supabase.client.postgrest["pacientes"].select(Columns.list("campos_custom")) {
                    filter { eq("id", pacienteId) }
                }.decodeList<JsonObject>().firstOrNull()?.get("campos_custom") as? JsonObject
                val mapa = valores?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.let { k to it } }?.toMap().orEmpty()
                // Sin datos de la ficha ni escala: no hace falta leer los campos.
                val defs = if (mapa.isEmpty()) emptyList()
                else campos ?: PacientesRepo.camposPaciente().map { it.id to it.nombre }.also { campos = it }
                alertasEstetica(defs, mapa, fototipo.await())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Las alertas que manda el servidor (resumen clínico): [{clave, icono, texto, tono}]. */
    fun parsear(e: kotlinx.serialization.json.JsonElement?): List<AlertaEstetica> =
        (e as? JsonArray)?.mapNotNull { x ->
            val o = x as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = (o[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }
            val texto = s("texto") ?: return@mapNotNull null
            AlertaEstetica(
                clave = s("clave").orEmpty(), icono = s("icono").orEmpty(), texto = texto,
                tono = when (s("tono")) { "riesgo" -> TonoAlerta.RIESGO; "aviso" -> TonoAlerta.AVISO; else -> TonoAlerta.INFO },
            )
        }.orEmpty()
}
