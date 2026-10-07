package pe.saniape.app.tutoriales

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** Qué tarea del contrato (§1.4) emite cada escritura aceptada por el servidor. */
class TareasEscrituraTest {

    private fun o(s: String) = Json.parseToJsonElement(s) as JsonObject
    private val vacio = o("{}")

    @Test
    fun citasPacientesYSesiones() {
        assertEquals(listOf("cita_creada"), TareasEscritura.de("/api/staff/cita/crear", vacio, null))
        assertEquals(listOf("paciente_creado"), TareasEscritura.de("/api/staff/paciente/crear", vacio, null))
        assertEquals(listOf("sesion_completada"), TareasEscritura.de("/api/staff/cita/completar", vacio, null))
        assertEquals(listOf("sesion_completada"), TareasEscritura.de("/api/staff/sesion/estado", o("""{"estado":"Completada"}"""), null))
        // Reprogramar / no asistió no completan nada.
        assertEquals(emptyList(), TareasEscritura.de("/api/staff/sesion/estado", o("""{"estado":"Reprogramada"}"""), null))
        assertEquals(emptyList(), TareasEscritura.de("/api/staff/cita/reprogramar", vacio, null))
    }

    @Test
    fun cobrosDeLaCita() {
        assertEquals(listOf("cobro_registrado"), TareasEscritura.de("/api/staff/cita/cobrar", o("""{"modo":"cobrar","metodo":"Yape"}"""), null))
        // "No cobrar" no registra dinero.
        assertEquals(emptyList(), TareasEscritura.de("/api/staff/cita/cobrar", o("""{"modo":"gratis"}"""), null))
        val dividido = o("""{"modo":"cobrar","pagos":[{"metodo":"Efectivo","monto":20},{"metodo":"Yape","monto":30}]}""")
        assertEquals(listOf("cobro_registrado", "cobro_dividido"), TareasEscritura.de("/api/staff/cita/cobrar", dividido, o("""{"ok":true}""")))
        // Repetido (ya estaba cobrada): no cuenta como dividido.
        assertEquals(listOf("cobro_registrado"), TareasEscritura.de("/api/staff/cita/cobrar", dividido, o("""{"yaEstaba":true}""")))
    }

    @Test
    fun pagosYSaldoAFavor() {
        assertEquals(listOf("cobro_registrado"), TareasEscritura.de("/api/staff/pago/registrar", o("""{"monto":50,"metodo":"Efectivo"}"""), null))
        val conSaldo = o("""{"pagos":[{"metodo":"Saldo a favor","monto":20},{"metodo":"Yape","monto":30}]}""")
        assertEquals(listOf("cobro_registrado", "pago_con_saldo"), TareasEscritura.de("/api/staff/pago/registrar", conSaldo, null))
    }

    @Test
    fun clinicoYTratamientos() {
        assertEquals(listOf("tratamiento_creado"), TareasEscritura.de("/api/staff/tratamiento/accion", o("""{"accion":"crear"}"""), null))
        assertEquals(emptyList(), TareasEscritura.de("/api/staff/tratamiento/accion", o("""{"accion":"editar"}"""), null))
        assertEquals(listOf("triaje_guardado"), TareasEscritura.de("/api/staff/atencion/triaje", vacio, null))
        assertEquals(listOf("atencion_medica_cerrada"), TareasEscritura.de("/api/staff/atencion/terminar", vacio, null))
        assertEquals(listOf("receta_emitida"), TareasEscritura.de("/api/staff/receta/emitir", vacio, null))
        assertEquals(listOf("evaluacion_psico_guardada"), TareasEscritura.de("/api/staff/evaluacion-psico/guardar", vacio, null))
        assertEquals(emptyList(), TareasEscritura.de("/api/staff/atencion/guardar", vacio, null))
    }
}
