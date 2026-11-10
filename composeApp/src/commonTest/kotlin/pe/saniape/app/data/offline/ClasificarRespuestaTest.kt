package pe.saniape.app.data.offline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * El 409 no siempre es "reintenta": solo el de idempotencia en curso. Un 409 de
 * negocio (sin cupo, ficha dada de baja, conciliación) reintentado da lo mismo
 * veinte veces mientras la app dice "se registrará al volver la señal".
 */
class ClasificarRespuestaTest {
    private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun okEsOk() {
        assertEquals(DestinoRespuesta.OK, clasificarRespuesta(200, j("""{"ok":true}""")))
    }

    @Test
    fun idempotenciaEnCursoSeReintenta() {
        assertEquals(DestinoRespuesta.REINTENTAR,
            clasificarRespuesta(409, j("""{"error":"Operación en curso, reintenta"}""")))
    }

    @Test
    fun conciliacionPendienteNoSeReintenta() {
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(409, j(
            """{"ok":false,"pendiente":true,"reintentable":false,"error":"Operación pendiente de conciliación. No la repitas con otra clave."}""")))
    }

    @Test
    fun negocioConCodigoNoSeReintenta() {
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(409, j(
            """{"error":"Ya no hay cupo en ese horario.","codigo":"capacidad_citas_agotada"}""")))
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(409, j(
            """{"error":"El paciente está dado de baja"}""")))
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(409, null))
    }

    /**
     * Dental Fase 1 · R4: el «crear tratamiento» encolado cuyas piezas ya tienen
     * tratamiento. Viene SIN `reintentable`: es final (se saca de la cola y se
     * avisa), nunca se reintenta.
     */
    @Test
    fun piezasYaPresupuestadasEsRechazoFinal() {
        val cuerpo = j("""{"ok":false,"codigo":"PIEZAS_YA_PRESUPUESTADAS","error":"Estas piezas ya tienen un tratamiento: actualiza el presupuesto"}""")
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(409, cuerpo))
        assertEquals(
            "Estas piezas ya tienen un tratamiento: actualiza el presupuesto",
            mensajeRechazoCola("tratamiento:accion", "PIEZAS_YA_PRESUPUESTADAS", "otro texto"),
        )
        assertEquals("No se pudo registrar «cita:crear»: Sin cupo", mensajeRechazoCola("cita:crear", null, "Sin cupo"))
    }

    @Test
    fun errores4xxSonRechazo() {
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(400, j(
            """{"error":"Indica quién atendió","codigo":"SIN_PROFESIONAL"}""")))
        assertEquals(DestinoRespuesta.RECHAZO, clasificarRespuesta(403, null))
    }
}
