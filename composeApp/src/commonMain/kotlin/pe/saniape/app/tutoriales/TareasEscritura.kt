package pe.saniape.app.tutoriales

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import pe.saniape.app.data.staff.esMetodoSaldo

/**
 * Qué TAREA del contrato (§1.4) cumple una escritura que el servidor aceptó (o
 * que quedó en la cola offline). PURA y en un solo lugar: toda escritura de la
 * app a `/api/staff/…` pasa por `enviarOEncolar` / los `postJson` de los repos,
 * así cada camino (agenda, ficha, Sesiones, consulta guiada, cola offline) avisa
 * igual sin que cada pantalla tenga que acordarse.
 *
 * Sin tutorial en curso el motor ignora la tarea: costo cero.
 */
object TareasEscritura {

    private fun JsonObject.texto(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull

    fun de(endpoint: String, cuerpo: JsonObject, respuesta: JsonObject?): List<String> {
        val ruta = endpoint.substringBefore('?').trimEnd('/')
        return when {
            ruta.endsWith("/api/staff/cita/crear") -> listOf("cita_creada")
            // paciente_creado NO va aquí: el alta no pasa por enviarOEncolar y lo
            // emite PacientesRepo.crearPaciente (una sola vez, con o sin señal).
            ruta.endsWith("/api/staff/cita/completar") -> listOf("sesion_completada")
            ruta.endsWith("/api/staff/sesion/estado") ->
                if (cuerpo.texto("estado") == "Completada") listOf("sesion_completada") else emptyList()
            ruta.endsWith("/api/staff/cita/cobrar") -> {
                if (cuerpo.texto("modo") == "gratis") emptyList()
                else buildList {
                    add("cobro_registrado")
                    val partes = (cuerpo["pagos"] as? JsonArray)?.size ?: 0
                    val yaEstaba = respuesta?.texto("yaEstaba") == "true"
                    if (partes > 1 && !yaEstaba) add("cobro_dividido")
                }
            }
            ruta.endsWith("/api/staff/pago/registrar") -> buildList {
                add("cobro_registrado")
                val conSaldo = (cuerpo["pagos"] as? JsonArray).orEmpty()
                    .any { esMetodoSaldo(((it as? JsonObject)?.get("metodo") as? JsonPrimitive)?.contentOrNull) }
                if (conSaldo) add("pago_con_saldo")
            }
            ruta.endsWith("/api/staff/tratamiento/accion") ->
                if (cuerpo.texto("accion") == "crear") listOf("tratamiento_creado") else emptyList()
            ruta.endsWith("/api/staff/atencion/triaje") -> listOf("triaje_guardado")
            ruta.endsWith("/api/staff/atencion/terminar") -> listOf("atencion_medica_cerrada")
            ruta.endsWith("/api/staff/receta/emitir") -> listOf("receta_emitida")
            ruta.endsWith("/api/staff/evaluacion-psico/guardar") -> listOf("evaluacion_psico_guardada")
            else -> emptyList()
        }
    }

    /** Avisa al motor (no hace nada si no hay tutorial en curso). */
    fun emitir(endpoint: String, cuerpo: JsonObject, respuesta: JsonObject?) {
        // Nunca puede afectar la escritura (resultado, clave de idempotencia, cola).
        runCatching {
            if (!MotorTutoriales.estado.enCurso) return
            de(endpoint, cuerpo, respuesta).forEach { MotorTutoriales.tarea(it) }
        }
    }
}
