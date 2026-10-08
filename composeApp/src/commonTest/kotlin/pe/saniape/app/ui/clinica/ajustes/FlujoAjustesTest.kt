package pe.saniape.app.ui.clinica.ajustes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * El flujo de atención en Ajustes (gemelo de SelectorFlujo / ConfigPorEspecialidad
 * de la web): cómo cambia el modo sin perder los nombres y cómo una especialidad
 * sigue a la clínica cuando no tiene flujo propio.
 */
class FlujoAjustesTest {

    private val fisio = Flujo()   // Consulta → Evaluación → Sesiones → Alta

    @Test
    fun deUnaCitaConservaElTipoInternoQueYaTenia() {
        // RENOVA: "Evaluación" es internamente una Evaluación; pasar a una sola cita no la vuelve Consulta.
        val renova = Flujo(usaConsulta = false, usaEvaluacion = true, labelEvaluacion = "Valoración")
        val dos = renova.conModo("dos", renova)
        assertEquals("dos", dos.modo)
        assertEquals("Consulta", dos.labelConsulta)
        // Pasar a dos citas no pisa el nombre que ya tenía (como SelectorFlujo de la web).
        assertEquals("Valoración", dos.labelEvaluacion)
        val otraVezUna = dos.conModo("una", renova)
        assertEquals("Evaluación", otraVezUna.entrada)
    }

    @Test
    fun directoASesionesNoTieneCitaDeEntrada() {
        val psico = fisio.conModo("ninguna", fisio)
        assertEquals(listOf("Sesión"), psico.tipos)
        assertEquals(listOf("Sesiones", "Alta"), psico.recorrido.map { it.first })
    }

    @Test
    fun nombresVaciosCaenAlGuardado() {
        val guardado = Flujo(labelSesiones = "Controles")
        val limpio = guardado.copy(labelSesiones = "  ", labelAlta = "").limpio(guardado)
        assertEquals("Controles", limpio.labelSesiones)
        assertEquals("Alta", limpio.labelAlta)
    }

    @Test
    fun especialidadSinLasDosBanderasSigueALaClinica() {
        val aMedias = buildJsonObject { put("usa_consulta", false) }
        assertEquals(fisio, Flujo.deEspecialidad(fisio, aMedias))
        val odonto = buildJsonObject {
            put("usa_consulta", true); put("usa_evaluacion", false); put("label_consulta", "Diagnóstico")
            put("label_alta", JsonPrimitive(""))
        }
        val f = Flujo.deEspecialidad(fisio, odonto)
        assertEquals("una", f.modo)
        assertEquals("Diagnóstico", f.nombreEntrada)
        assertEquals("Alta", f.labelAlta)   // vacío = el de la clínica
    }

    @Test
    fun jsonLlevaLosSeisCampos() {
        assertTrue(fisio.json().keys.size == 6)
    }
}
