package pe.saniape.app.ui.clinica.ajustes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ajustes → Historia clínica / consentimiento (web 2026-10-15): sin HC de norma
 * no hay IPRESS ni típicos; un servidor viejo (sin `historiaMedica`) se ve igual
 * que siempre.
 */
class HistoriaClinicaAjustesTest {

    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test fun servidorViejoMuestraTodoComoSiempre() {
        val viejo = obj("""{"especialidades":[{"id":"f","nombre":"Psiquiatría"}],"faltanBiblioteca":0,"plantillas":[]}""")
        assertTrue(seccionConHistoriaMedica(viejo))
        assertTrue(seccionConHistoriaMedica(null))
        assertFalse(bibliotecaSinModelos(viejo))
    }

    @Test fun soloConsentimientoOcultaLaHcDeNorma() {
        assertFalse(seccionConHistoriaMedica(obj("""{"historiaMedica":false}""")))
        assertTrue(seccionConHistoriaMedica(obj("""{"historiaMedica":true}""")))
    }

    @Test fun bibliotecaSinModelosComoLaWeb() {
        // Psiquiatría sola: nada que agregar y ninguna de la biblioteca → el aviso.
        assertTrue(bibliotecaSinModelos(obj(
            """{"historiaMedica":true,"especialidades":[{"id":"q","nombre":"Psiquiatría"}],"faltanBiblioteca":0,"plantillas":[{"id":"1","clave_biblioteca":null}]}""",
        )))
        // Falta agregar: hay modelos.
        assertFalse(bibliotecaSinModelos(obj(
            """{"historiaMedica":false,"especialidades":[{"id":"e","nombre":"Estética"}],"faltanBiblioteca":3,"plantillas":[]}""",
        )))
        // Ya agregó la biblioteca: hay modelos.
        assertFalse(bibliotecaSinModelos(obj(
            """{"historiaMedica":false,"especialidades":[{"id":"e","nombre":"Estética"}],"faltanBiblioteca":0,"plantillas":[{"id":"1","clave_biblioteca":"est-peeling"}]}""",
        )))
        // Sin especialidades (aún no cargan): no se dice.
        assertFalse(bibliotecaSinModelos(obj("""{"historiaMedica":true,"especialidades":[],"faltanBiblioteca":0,"plantillas":[]}""")))
        assertTrue(SIN_MODELOS_BIBLIOTECA.contains("crear el tuyo"))
    }
}
