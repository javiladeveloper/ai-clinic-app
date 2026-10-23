package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Buscador de los selectores de servicio (gemelo de lib/__tests__/buscar-opciones.test.ts, web). */
class BuscarOpcionesTest {

    // Catálogo de Centro OMER (captura del dueño, 24/10/2026): mayúsculas, minúsculas y tildes mezcladas.
    private val omer = listOf(
        "Extirpación de lesión de piel",
        "Aplicación de inyectable",
        "Curación de herida",
        "TERAPIA FISICA",
        "VITAMINOTERAPIA",
        "MANEJO REGENERATIVO DEL DOLOR DE ESPALDAS Y/O CIATICA",
        "OZONOTERAPIA MEDICA",
        "manejo regenerativo del dolor de rodillas",
        "EVALUACION ESTETICA",
    )

    private fun nombres(q: String?) = filtrarOpciones(omer, q) { it }

    @Test
    fun normalizaSinTildesNiMayusculas() {
        assertEquals("extirpacion de lesion", normalizarParaBuscar("Extirpación de LESIÓN"))
        assertEquals("nino", normalizarParaBuscar("Niño"))
        assertEquals("y o", normalizarParaBuscar("Y/O"))
        assertEquals("", normalizarParaBuscar(null))
        assertEquals(listOf("dolor", "rodilla"), palabrasDeConsulta("  Dolor   dolor, RODILLA "))
        assertTrue(palabrasDeConsulta("").isEmpty())
    }

    @Test
    fun consultaVaciaDevuelveTodas() {
        assertEquals(omer, nombres(""))
        assertEquals(omer, nombres("   "))
        assertEquals(omer, nombres(null))
    }

    @Test
    fun sinTildesEnNingunLado() {
        assertEquals(listOf("TERAPIA FISICA"), nombres("fisica"))
        assertEquals(listOf("TERAPIA FISICA"), nombres("FÍSICA"))
        assertEquals(listOf("Curación de herida"), nombres("curacion"))
        assertEquals(listOf("EVALUACION ESTETICA"), nombres("evaluación estética"))
    }

    @Test
    fun palabrasEnCualquierOrdenYTodas() {
        assertEquals(listOf("manejo regenerativo del dolor de rodillas"), nombres("dolor rodilla"))
        assertEquals(listOf("manejo regenerativo del dolor de rodillas"), nombres("rodilla dolor"))
        assertEquals(listOf("MANEJO REGENERATIVO DEL DOLOR DE ESPALDAS Y/O CIATICA"), nombres("regen ciat"))
        assertTrue(nombres("dolor hombro").isEmpty())
        assertFalse(coincideConsulta("TERAPIA FISICA", "terapia rodilla"))
        assertTrue(coincideConsulta("TERAPIA FISICA", "terapia"))
    }

    @Test
    fun ordenEmpiezaPalabraContiene() {
        assertEquals(listOf("TERAPIA FISICA", "VITAMINOTERAPIA", "OZONOTERAPIA MEDICA"), nombres("terapia"))
        val ops = listOf("Biomedicamento", "Ozonoterapia médica")
        assertEquals(listOf("Ozonoterapia médica", "Biomedicamento"), filtrarOpciones(ops, "medica") { it })
    }

    @Test
    fun elDetalleNoEntraEnLaBusqueda() {
        // Se busca solo por la etiqueta que se pasa (el precio va aparte).
        data class Op(val nombre: String, val detalle: String)
        assertTrue(filtrarOpciones(listOf(Op("Masaje", "S/ 150.00")), "150") { it.nombre }.isEmpty())
    }

    private fun texto(t: List<TramoResaltado>) = t.joinToString("") { it.texto }

    @Test
    fun resaltaSobreElTextoOriginal() {
        assertEquals(listOf(TramoResaltado("TERAPIA FISICA", false)), resaltarCoincidencias("TERAPIA FISICA", ""))
        assertTrue(resaltarCoincidencias("", "x").isEmpty())
        assertEquals(
            listOf(TramoResaltado("Extirpación de ", false), TramoResaltado("lesión", true)),
            resaltarCoincidencias("Extirpación de lesión", "lesion"),
        )
        assertEquals(listOf(TramoResaltado("Curación", true)), resaltarCoincidencias("Curación", "CURACIÓN"))
    }

    @Test
    fun resaltaCadaPalabraYTodasSusApariciones() {
        val t = resaltarCoincidencias("manejo regenerativo del dolor de rodillas", "rodilla dolor")
        assertEquals(listOf("dolor", "rodilla"), t.filter { it.coincide }.map { it.texto })
        assertEquals("manejo regenerativo del dolor de rodillas", texto(t))
        assertEquals(listOf("Dolor", "dolor"), resaltarCoincidencias("Dolor y más dolor", "dolor").filter { it.coincide }.map { it.texto })
    }

    @Test
    fun tramosQueSeTocanSeJuntanYLosEmojisQuedan() {
        assertEquals(listOf(TramoResaltado("ozonoterapia", true)), resaltarCoincidencias("ozonoterapia", "ozono terapia"))
        val conEmoji = resaltarCoincidencias("🦷 Limpieza dental", "limpieza")
        assertEquals("🦷 Limpieza dental", texto(conEmoji))
        assertEquals("Limpieza", conEmoji.first { it.coincide }.texto)
    }
}
